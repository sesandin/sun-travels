import catalogue from './countries.json';

// Generated ISO data and source/license details: backend/src/main/resources/location/README.md.
export const countries = catalogue;
export function findCountry(value: string) {
  const key = value.trim().toLocaleUpperCase('en');
  return countries.find((country) =>
    [country.code, country.name, ...country.aliases].some(
      (alias) => alias.toLocaleUpperCase('en') === key,
    ),
  );
}
