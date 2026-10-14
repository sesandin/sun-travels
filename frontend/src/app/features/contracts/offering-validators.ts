import { ValidatorFn } from '@angular/forms';
export const money: ValidatorFn = (c) =>
  /^\d{1,8}(\.\d{1,2})?$/.test(String(c.value)) && Number(c.value) > 0 ? null : { money: true };
export const positiveInteger: ValidatorFn = (c) =>
  Number.isInteger(c.value) && c.value > 0 && c.value <= 2147483647
    ? null
    : { positiveInteger: true };
export const period: ValidatorFn = (c) => {
  const a = c.get('validFrom')?.value,
    b = c.get('validTo')?.value;
  return a && b && a > b ? { period: true } : null;
};
