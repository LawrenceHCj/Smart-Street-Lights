import request from './request'

export interface EnergyTrendPoint {
  ts: number
  baselineEnergyKwh: number
  estimatedEnergyKwh: number
  savedEnergyKwh: number
}

export interface DeviceEnergySaving {
  deviceId: string
  baselineEnergyKwh: number
  estimatedEnergyKwh: number
  savedEnergyKwh: number
  savingRatePercent: number
  coverageHours: number
}

export interface PeriodEnergySaving {
  name: string
  brightnessPercent: number
  baselineEnergyKwh: number
  estimatedEnergyKwh: number
  savedEnergyKwh: number
  coverageHours: number
}

export interface EnergySavingsReport {
  generatedAt: number
  startTs: number
  endTs: number
  days: number
  coveredDeviceCount: number
  sampleBucketCount: number
  coverageHours: number
  baselineEnergyKwh: number
  estimatedEnergyKwh: number
  savedEnergyKwh: number
  savingRatePercent: number
  averageBrightnessPercent: number
  estimatedCostSavingYuan: number
  estimatedCarbonReductionKg: number
  electricityPriceYuanPerKwh: number
  carbonFactorKgPerKwh: number
  calculationMethod: 'ESTIMATED_FROM_REFERENCE_POWER_AND_SCHEDULE'
  trend: EnergyTrendPoint[]
  devices: DeviceEnergySaving[]
  periods: PeriodEnergySaving[]
}

export function getEnergySavings(days: number): Promise<EnergySavingsReport> {
  return request.get('/energy-savings', { params: { days }, silent: true })
}
