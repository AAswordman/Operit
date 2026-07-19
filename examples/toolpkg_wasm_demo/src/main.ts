import { isPrime, nthPrime } from "./wasm/core";

function positiveInteger(value: number, name: string): number {
  if (!Number.isInteger(value) || value < 1) {
    throw new Error(`${name} must be a positive integer`);
  }
  return value;
}

export async function is_prime(params: { n: number }) {
  const n = positiveInteger(params.n, "n");
  return {
    n,
    is_prime: await isPrime(n),
  };
}

export async function nth_prime(params: { index: number }) {
  const index = positiveInteger(params.index, "index");
  return {
    index,
    prime: await nthPrime(index),
  };
}
