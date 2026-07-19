/// <reference path="./assemblyscript-env.d.ts" />

export function isPrime(n: i32): i32 {
  if (n < 2) {
    return 0;
  }
  if (n === 2) {
    return 1;
  }
  if (n % 2 === 0) {
    return 0;
  }

  for (let divisor: i32 = 3; divisor <= n / divisor; divisor += 2) {
    if (n % divisor === 0) {
      return 0;
    }
  }

  return 1;
}

export function nthPrime(index: i32): i32 {
  if (index < 1) {
    return 0;
  }

  let found: i32 = 0;
  let candidate: i32 = 1;

  while (found < index) {
    candidate += 1;
    if (isPrime(candidate) === 1) {
      found += 1;
    }
  }

  return candidate;
}
