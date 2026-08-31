// 【5号代做·需与2号对账】语音输入封装：基于 Edge/Chrome 原生 Web Speech API
// （webkitSpeechRecognition 在线语音识别 + speechSynthesis 语音合成），零依赖、零后端改动。
//
// 两种输入方式（均无需唤醒词，鼠标位置不影响）：
//  1. 按住说话：mousedown 开始收集、松开结束并送问答；
//  2. 单击聆听：单击麦克风后开始聆听，说完停顿约 3 秒自动结束并送问答；
//     聆听中可直接说"确认"/"取消"操作待确认卡片（按 actionId 调确认接口，与点卡片同一链路）。
//
// 可靠性处理：
//  - 浏览器要求语音识别在用户手势内启动：保持一个常驻麦克风采集流豁免该限制，
//    使识别在静音中断后可以自动重启（用户说话停顿超过阈值也不会丢后半句）；
//  - 识别对象异常（InvalidStateError）时销毁重建自愈；
//  - 识别依赖浏览器在线语音服务（Edge 为微软语音服务），仅 localhost/HTTPS 可用。
//
// 注意：播报期间麦克风不监听（播报在指令发送之后才开始）。

export type VoiceState = 'idle' | 'listening' | 'speaking'

export interface VoiceCallbacks {
  /** 识别到完整指令文本（送问答） */
  onCommand: (text: string) => void
  /** 识别到语音关键字：confirm=确认执行待确认操作，cancel=取消 */
  onKeyword: (keyword: 'confirm' | 'cancel') => void
  /** 状态变化（idle/listening/speaking），用于 UI 指示 */
  onStateChange: (state: VoiceState) => void
  /** 实时转写预览（含未定稿内容） */
  onInterim?: (text: string) => void
  /** 识别不可用/被拒绝等错误 */
  onError?: (message: string) => void
}

// TS DOM lib 未内置 SpeechRecognition 类型，仅做最小声明
interface SpeechRecognitionLike {
  lang: string
  continuous: boolean
  interimResults: boolean
  maxAlternatives: number
  onresult: ((event: SpeechRecognitionEventLike) => void) | null
  onerror: ((event: { error: string }) => void) | null
  onstart: (() => void) | null
  onend: (() => void) | null
  start: () => void
  stop: () => void
  abort: () => void
}
type SpeechRecognitionCtor = new () => SpeechRecognitionLike
interface SpeechRecognitionEventLike {
  resultIndex: number
  results: ArrayLike<{ isFinal: boolean; 0: { transcript: string } }>
}

const COMMAND_KEYWORDS: Array<{ regex: RegExp; keyword: 'confirm' | 'cancel' }> = [
  { regex: /^(确认|确定|确认执行|可以|好的)$/, keyword: 'confirm' },
  { regex: /^(取消|算了|不要了|不用了)$/, keyword: 'cancel' },
]
const SILENCE_MS = 3000 // 静音自动结束并发送

export function useVoice(callbacks: VoiceCallbacks) {
  const impl = (window as unknown as Record<string, unknown>).SpeechRecognition
    ?? (window as unknown as Record<string, unknown>).webkitSpeechRecognition
  const supported = typeof impl === 'function' && 'speechSynthesis' in window

  let recognition: SpeechRecognitionLike | null = null
  let mode: 'idle' | 'listening' = 'idle'
  let finalText = ''
  let silenceTimer: number | null = null
  let ttsOn = true
  let zhVoice: SpeechSynthesisVoice | null = null
  let restartTimer: number | null = null
  let running = false // 识别会话是否在运行（onstart/onend 维护，避免 start 竞态）
  let startFailures = 0 // 连续启动失败计数（InvalidStateError 自愈重试上限）
  let keepaliveStream: MediaStream | null = null // 常驻音频采集流：豁免浏览器"识别启动需用户手势"限制

  // ============ 状态与 TTS ============

  function modeState(): VoiceState {
    return mode === 'listening' ? 'listening' : 'idle'
  }

  function setMode(next: 'idle' | 'listening') {
    mode = next
    if (silenceTimer !== null) {
      window.clearTimeout(silenceTimer)
      silenceTimer = null
    }
    if (next === 'listening') resetSilence()
    callbacks.onStateChange(modeState())
  }

  function resetSilence() {
    if (silenceTimer !== null) window.clearTimeout(silenceTimer)
    silenceTimer = window.setTimeout(() => {
      // 静音超时：结束收集并送问答
      if (mode === 'listening') finalize()
    }, SILENCE_MS)
  }

  function pickZhVoice(): SpeechSynthesisVoice | null {
    if (zhVoice) return zhVoice
    const voices = window.speechSynthesis.getVoices()
    zhVoice = voices.find((v) => v.lang === 'zh-CN' && /xiaoxiao|huihui/i.test(v.name))
      ?? voices.find((v) => v.lang.toLowerCase().startsWith('zh'))
      ?? null
    return zhVoice
  }

  /** 去掉不适合朗读的部分：来源标注行、Markdown 符号、内部标识 */
  function cleanForSpeech(text: string): string {
    return text
      .replace(/信息来源[：:].*$/gm, '')
      .replace(/actionId[:：]\s*\S+/g, '')
      .replace(/[#*_`>~|-]/g, '')
      .replace(/\n+/g, '，')
      .replace(/\s+/g, ' ')
      .trim()
  }

  /** 语音播报（仅当播报开关开启） */
  function speak(text: string) {
    if (!supported || !ttsOn) return
    const cleaned = cleanForSpeech(text)
    if (!cleaned) return
    window.speechSynthesis.cancel()
    const utter = new SpeechSynthesisUtterance(cleaned)
    utter.lang = 'zh-CN'
    utter.rate = 1.05
    const voice = pickZhVoice()
    if (voice) utter.voice = voice
    callbacks.onStateChange('speaking')
    utter.onend = () => callbacks.onStateChange(modeState())
    utter.onerror = () => callbacks.onStateChange(modeState())
    window.speechSynthesis.speak(utter)
  }

  function stopSpeak() {
    if ('speechSynthesis' in window) window.speechSynthesis.cancel()
    if (modeState() === 'speaking') callbacks.onStateChange(modeState())
  }

  function setTts(on: boolean) {
    ttsOn = on
    if (!on) stopSpeak()
  }

  // ============ 识别 ============

  function ensureRecognition() {
    if (recognition) return recognition
    const Ctor = impl as SpeechRecognitionCtor
    recognition = new Ctor()
    recognition.lang = 'zh-CN'
    recognition.continuous = true
    recognition.interimResults = true
    recognition.maxAlternatives = 1

    recognition.onresult = (event) => {
      if (mode === 'idle') return
      let interim = ''
      let final = ''
      for (let i = event.resultIndex; i < event.results.length; i++) {
        const r = event.results[i]
        if (r.isFinal) final += r[0].transcript
        else interim += r[0].transcript
      }
      const text = (final + interim).trim()
      if (!text) return
      resetSilence()
      // 指令收集：累加定稿文本，实时转写显示"定稿 + 未定稿"
      if (final) finalText += final
      callbacks.onInterim?.(finalText + interim)
    }

    recognition.onstart = () => {
      running = true
      startFailures = 0
    }

    recognition.onerror = (event) => {
      if (event.error === 'not-allowed' || event.error === 'service-not-allowed') {
        setMode('idle')
        callbacks.onError?.('麦克风权限被拒绝，请在浏览器地址栏允许麦克风访问后重试')
      } else if (event.error === 'audio-capture') {
        setMode('idle')
        callbacks.onError?.('未检测到麦克风设备')
      } else if (event.error === 'network') {
        setMode('idle')
        callbacks.onError?.('语音识别服务网络不可用（浏览器在线语音服务需要联网）')
      }
      // no-speech / aborted：静音或主动停止，忽略
    }

    recognition.onend = () => {
      running = false
      // 在线识别服务会在静音后自动中断：聆听中自动重启（常驻采集流已豁免用户手势限制），
      // 保证用户说话停顿超过阈值也不会丢后半句
      if (mode !== 'idle') {
        if (restartTimer !== null) window.clearTimeout(restartTimer)
        restartTimer = window.setTimeout(() => {
          restartTimer = null
          startIfNeeded()
        }, 200)
      }
    }

    // 部分浏览器异步加载语音包
    window.speechSynthesis.onvoiceschanged = () => {
      zhVoice = null
      pickZhVoice()
    }
    pickZhVoice()
    return recognition
  }

  /** 常驻音频采集流：Chromium 要求语音识别 start() 在用户手势内调用，
   *  识别静音中断后的自动重启需要页面持有活跃的音频采集会话才会被允许。 */
  function startAudioKeepalive() {
    if (keepaliveStream || !navigator.mediaDevices?.getUserMedia) return
    navigator.mediaDevices
      .getUserMedia({ audio: true })
      .then((stream) => {
        keepaliveStream = stream
      })
      .catch(() => {
        /* 权限问题由识别 onerror 统一提示 */
      })
  }

  /** 开始聆听（按住说话 / 单击聆听共用）：不先停再启，复用运行中的识别会话 */
  function switchToListening() {
    if (!supported) {
      callbacks.onError?.('当前浏览器不支持语音识别，请使用 Edge 或 Chrome 浏览器')
      return
    }
    stopSpeak() // 开口即打断播报
    finalText = ''
    setMode('listening')
    startAudioKeepalive()
    startIfNeeded()
  }

  /** 仅当识别会话不在运行时才启动（onstart/onend 维护 running 标志） */
  function startIfNeeded() {
    if (running || mode === 'idle') return
    try {
      ensureRecognition().start()
    } catch (e) {
      // InvalidStateError：识别对象可能处于异常状态（如启动中被 stop）。
      // 销毁重建后延迟重试（最多 3 次），而不是直接报错。
      if ((e as { name?: string })?.name === 'InvalidStateError') {
        startFailures++
        if (startFailures > 3) {
          startFailures = 0
          setMode('idle')
          callbacks.onError?.('语音识别无法启动，请刷新页面重试')
          return
        }
        try {
          recognition?.abort()
        } catch {
          /* 忽略 */
        }
        recognition = null
        if (restartTimer === null) {
          restartTimer = window.setTimeout(() => {
            restartTimer = null
            startIfNeeded()
          }, 300)
        }
        return
      }
      startFailures = 0
      if (!running) callbacks.onError?.('语音识别启动失败，请刷新页面重试')
    }
  }

  /** 收尾：停止识别，处理关键字/完整指令 */
  function finalize() {
    if (mode === 'idle') return
    setMode('idle')
    try {
      recognition?.stop()
    } catch {
      /* 已停止，忽略 */
    }
    const text = finalText.trim()
    finalText = ''
    callbacks.onInterim?.('')
    if (!text) return
    const kw = COMMAND_KEYWORDS.find((k) => k.regex.test(text))
    if (kw) callbacks.onKeyword(kw.keyword)
    else callbacks.onCommand(text)
  }

  function destroy() {
    if (silenceTimer !== null) window.clearTimeout(silenceTimer)
    if (restartTimer !== null) window.clearTimeout(restartTimer)
    mode = 'idle'
    try {
      recognition?.abort()
    } catch {
      /* 忽略 */
    }
    recognition = null
    keepaliveStream?.getTracks().forEach((t) => t.stop())
    keepaliveStream = null
    stopSpeak()
  }

  return {
    supported,
    // 按住说话 / 单击聆听
    startPtt: () => switchToListening(),
    endPtt: () => finalize(),
    stopAll: () => destroy(),
    // 播报
    speak,
    stopSpeak,
    setTts,
  }
}
