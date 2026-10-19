export function isContractExpired(validTo: string): boolean {
  // Compare calendar dates without interpreting an ISO date as UTC.
  const today = new Date();
  const todayDate = [
    today.getFullYear(),
    String(today.getMonth() + 1).padStart(2, '0'),
    String(today.getDate()).padStart(2, '0'),
  ].join('-');
  return validTo < todayDate;
}
