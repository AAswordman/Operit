#include <jni.h>
#include <fcntl.h>
#include <unistd.h>

#include <algorithm>
#include <array>
#include <cstdint>
#include <cstring>
#include <limits>
#include <random>
#include <string>
#include <vector>

#include "toolpkg_protection_secret.h"

namespace {

constexpr std::array<uint8_t, 8> kMagic = {'O', 'P', 'T', 'P', 'R', 'O', 'T', 'A'};
constexpr size_t kNonceSize = 12;
constexpr size_t kTagSize = 16;
constexpr size_t kHeaderSize = kMagic.size() + kNonceSize + kTagSize;

constexpr std::array<uint32_t, 64> kSha256K = {
        0x428a2f98U, 0x71374491U, 0xb5c0fbcfU, 0xe9b5dba5U, 0x3956c25bU, 0x59f111f1U, 0x923f82a4U, 0xab1c5ed5U,
        0xd807aa98U, 0x12835b01U, 0x243185beU, 0x550c7dc3U, 0x72be5d74U, 0x80deb1feU, 0x9bdc06a7U, 0xc19bf174U,
        0xe49b69c1U, 0xefbe4786U, 0x0fc19dc6U, 0x240ca1ccU, 0x2de92c6fU, 0x4a7484aaU, 0x5cb0a9dcU, 0x76f988daU,
        0x983e5152U, 0xa831c66dU, 0xb00327c8U, 0xbf597fc7U, 0xc6e00bf3U, 0xd5a79147U, 0x06ca6351U, 0x14292967U,
        0x27b70a85U, 0x2e1b2138U, 0x4d2c6dfcU, 0x53380d13U, 0x650a7354U, 0x766a0abbU, 0x81c2c92eU, 0x92722c85U,
        0xa2bfe8a1U, 0xa81a664bU, 0xc24b8b70U, 0xc76c51a3U, 0xd192e819U, 0xd6990624U, 0xf40e3585U, 0x106aa070U,
        0x19a4c116U, 0x1e376c08U, 0x2748774cU, 0x34b0bcb5U, 0x391c0cb3U, 0x4ed8aa4aU, 0x5b9cca4fU, 0x682e6ff3U,
        0x748f82eeU, 0x78a5636fU, 0x84c87814U, 0x8cc70208U, 0x90befffaU, 0xa4506cebU, 0xbef9a3f7U, 0xc67178f2U};

uint32_t rotr(uint32_t value, uint32_t bits) {
    return (value >> bits) | (value << (32U - bits));
}

std::array<uint8_t, 32> sha256(const uint8_t* data, size_t length) {
    uint32_t h[8] = {
            0x6a09e667U, 0xbb67ae85U, 0x3c6ef372U, 0xa54ff53aU,
            0x510e527fU, 0x9b05688cU, 0x1f83d9abU, 0x5be0cd19U};

    std::vector<uint8_t> padded(data, data + length);
    padded.push_back(0x80U);
    while ((padded.size() % 64U) != 56U) {
        padded.push_back(0U);
    }
    const uint64_t bit_length = static_cast<uint64_t>(length) * 8U;
    for (int shift = 56; shift >= 0; shift -= 8) {
        padded.push_back(static_cast<uint8_t>((bit_length >> shift) & 0xffU));
    }

    for (size_t offset = 0; offset < padded.size(); offset += 64) {
        uint32_t w[64] = {};
        for (int index = 0; index < 16; ++index) {
            const size_t base = offset + static_cast<size_t>(index) * 4U;
            w[index] =
                    (static_cast<uint32_t>(padded[base]) << 24U) |
                    (static_cast<uint32_t>(padded[base + 1]) << 16U) |
                    (static_cast<uint32_t>(padded[base + 2]) << 8U) |
                    static_cast<uint32_t>(padded[base + 3]);
        }
        for (int index = 16; index < 64; ++index) {
            const uint32_t s0 = rotr(w[index - 15], 7U) ^ rotr(w[index - 15], 18U) ^ (w[index - 15] >> 3U);
            const uint32_t s1 = rotr(w[index - 2], 17U) ^ rotr(w[index - 2], 19U) ^ (w[index - 2] >> 10U);
            w[index] = w[index - 16] + s0 + w[index - 7] + s1;
        }

        uint32_t a = h[0];
        uint32_t b = h[1];
        uint32_t c = h[2];
        uint32_t d = h[3];
        uint32_t e = h[4];
        uint32_t f = h[5];
        uint32_t g = h[6];
        uint32_t hh = h[7];

        for (int index = 0; index < 64; ++index) {
            const uint32_t s1 = rotr(e, 6U) ^ rotr(e, 11U) ^ rotr(e, 25U);
            const uint32_t ch = (e & f) ^ ((~e) & g);
            const uint32_t temp1 = hh + s1 + ch + kSha256K[index] + w[index];
            const uint32_t s0 = rotr(a, 2U) ^ rotr(a, 13U) ^ rotr(a, 22U);
            const uint32_t maj = (a & b) ^ (a & c) ^ (b & c);
            const uint32_t temp2 = s0 + maj;
            hh = g;
            g = f;
            f = e;
            e = d + temp1;
            d = c;
            c = b;
            b = a;
            a = temp1 + temp2;
        }

        h[0] += a;
        h[1] += b;
        h[2] += c;
        h[3] += d;
        h[4] += e;
        h[5] += f;
        h[6] += g;
        h[7] += hh;
    }

    std::array<uint8_t, 32> out = {};
    for (int index = 0; index < 8; ++index) {
        out[static_cast<size_t>(index) * 4U] = static_cast<uint8_t>((h[index] >> 24U) & 0xffU);
        out[static_cast<size_t>(index) * 4U + 1U] = static_cast<uint8_t>((h[index] >> 16U) & 0xffU);
        out[static_cast<size_t>(index) * 4U + 2U] = static_cast<uint8_t>((h[index] >> 8U) & 0xffU);
        out[static_cast<size_t>(index) * 4U + 3U] = static_cast<uint8_t>(h[index] & 0xffU);
    }
    return out;
}

std::array<uint8_t, 32> sha256(const std::vector<uint8_t>& data) {
    return sha256(data.data(), data.size());
}

std::array<uint8_t, 32> hmacSha256(const std::vector<uint8_t>& key, const std::vector<uint8_t>& message) {
    std::array<uint8_t, 64> block_key = {};
    if (key.size() > block_key.size()) {
        const auto hashed = sha256(key);
        std::copy(hashed.begin(), hashed.end(), block_key.begin());
    } else {
        std::copy(key.begin(), key.end(), block_key.begin());
    }

    std::vector<uint8_t> inner;
    std::vector<uint8_t> outer;
    inner.reserve(block_key.size() + message.size());
    outer.reserve(block_key.size() + 32U);
    for (uint8_t value : block_key) {
        inner.push_back(static_cast<uint8_t>(value ^ 0x36U));
        outer.push_back(static_cast<uint8_t>(value ^ 0x5cU));
    }
    inner.insert(inner.end(), message.begin(), message.end());
    const auto inner_hash = sha256(inner);
    outer.insert(outer.end(), inner_hash.begin(), inner_hash.end());
    return sha256(outer);
}

std::vector<uint8_t> protectionKey() {
    std::vector<uint8_t> secret;
    secret.reserve(kToolPkgProtectionSecretBytes.size());
    for (uint8_t value : kToolPkgProtectionSecretBytes) {
        secret.push_back(static_cast<uint8_t>(value ^ kToolPkgProtectionSecretMask));
    }
    return secret;
}

bool secretConfigured() {
    return !protectionKey().empty();
}

bool fillRandom(uint8_t* data, size_t size) {
    size_t offset = 0;
    const int fd = open("/dev/urandom", O_RDONLY | O_CLOEXEC);
    if (fd >= 0) {
        while (offset < size) {
            const ssize_t count = read(fd, data + offset, size - offset);
            if (count <= 0) break;
            offset += static_cast<size_t>(count);
        }
        close(fd);
    }
    if (offset == size) return true;

    std::random_device rd;
    for (; offset < size; ++offset) {
        data[offset] = static_cast<uint8_t>(rd() & 0xffU);
    }
    return true;
}

uint32_t load32Le(const uint8_t* data) {
    return static_cast<uint32_t>(data[0]) |
           (static_cast<uint32_t>(data[1]) << 8U) |
           (static_cast<uint32_t>(data[2]) << 16U) |
           (static_cast<uint32_t>(data[3]) << 24U);
}

void store32Le(uint8_t* out, uint32_t value) {
    out[0] = static_cast<uint8_t>(value & 0xffU);
    out[1] = static_cast<uint8_t>((value >> 8U) & 0xffU);
    out[2] = static_cast<uint8_t>((value >> 16U) & 0xffU);
    out[3] = static_cast<uint8_t>((value >> 24U) & 0xffU);
}

uint32_t rotl(uint32_t value, uint32_t bits) {
    return (value << bits) | (value >> (32U - bits));
}

void chachaQuarterRound(uint32_t& a, uint32_t& b, uint32_t& c, uint32_t& d) {
    a += b;
    d ^= a;
    d = rotl(d, 16U);
    c += d;
    b ^= c;
    b = rotl(b, 12U);
    a += b;
    d ^= a;
    d = rotl(d, 8U);
    c += d;
    b ^= c;
    b = rotl(b, 7U);
}

std::array<uint8_t, 64> chacha20Block(
        const std::array<uint8_t, 32>& key,
        const std::array<uint8_t, kNonceSize>& nonce,
        uint32_t counter) {
    std::array<uint32_t, 16> state = {
            0x61707865U, 0x3320646eU, 0x79622d32U, 0x6b206574U,
            load32Le(key.data()),
            load32Le(key.data() + 4),
            load32Le(key.data() + 8),
            load32Le(key.data() + 12),
            load32Le(key.data() + 16),
            load32Le(key.data() + 20),
            load32Le(key.data() + 24),
            load32Le(key.data() + 28),
            counter,
            load32Le(nonce.data()),
            load32Le(nonce.data() + 4),
            load32Le(nonce.data() + 8)};

    auto working = state;
    for (int round = 0; round < 10; ++round) {
        chachaQuarterRound(working[0], working[4], working[8], working[12]);
        chachaQuarterRound(working[1], working[5], working[9], working[13]);
        chachaQuarterRound(working[2], working[6], working[10], working[14]);
        chachaQuarterRound(working[3], working[7], working[11], working[15]);
        chachaQuarterRound(working[0], working[5], working[10], working[15]);
        chachaQuarterRound(working[1], working[6], working[11], working[12]);
        chachaQuarterRound(working[2], working[7], working[8], working[13]);
        chachaQuarterRound(working[3], working[4], working[9], working[14]);
    }

    std::array<uint8_t, 64> out = {};
    for (size_t index = 0; index < working.size(); ++index) {
        store32Le(out.data() + index * 4U, working[index] + state[index]);
    }
    return out;
}

std::vector<uint8_t> chacha20Xor(
        const std::vector<uint8_t>& input,
        const std::array<uint8_t, 32>& key,
        const std::array<uint8_t, kNonceSize>& nonce) {
    std::vector<uint8_t> output(input.size());
    uint32_t counter = 1;
    size_t offset = 0;
    while (offset < input.size()) {
        const auto block = chacha20Block(key, nonce, counter++);
        const size_t block_count = std::min(block.size(), input.size() - offset);
        for (size_t index = 0; index < block_count; ++index) {
            output[offset + index] = static_cast<uint8_t>(input[offset + index] ^ block[index]);
        }
        offset += block_count;
    }
    return output;
}

std::array<uint8_t, 32> deriveAeadKey() {
    const auto secret = protectionKey();
    std::vector<uint8_t> salt;
    const char salt_text[] = "operit-toolpkg-protection-aead-salt";
    salt.insert(salt.end(), salt_text, salt_text + sizeof(salt_text) - 1U);
    const auto prk = hmacSha256(salt, secret);

    std::vector<uint8_t> prk_key(prk.begin(), prk.end());
    std::vector<uint8_t> info;
    const char info_text[] = "operit-toolpkg-chacha20-poly1305";
    info.insert(info.end(), info_text, info_text + sizeof(info_text) - 1U);
    info.push_back(1U);

    const auto okm = hmacSha256(prk_key, info);
    return okm;
}

void appendPadding16(std::vector<uint8_t>& data, size_t original_size) {
    const size_t padding = (16U - (original_size % 16U)) % 16U;
    data.insert(data.end(), padding, 0U);
}

void appendLittleEndian64(std::vector<uint8_t>& data, uint64_t value) {
    for (int shift = 0; shift < 64; shift += 8) {
        data.push_back(static_cast<uint8_t>((value >> shift) & 0xffU));
    }
}

std::vector<uint8_t> buildAssociatedData(const std::array<uint8_t, kNonceSize>& nonce) {
    std::vector<uint8_t> associated_data;
    associated_data.reserve(kMagic.size() + nonce.size());
    associated_data.insert(associated_data.end(), kMagic.begin(), kMagic.end());
    associated_data.insert(associated_data.end(), nonce.begin(), nonce.end());
    return associated_data;
}

std::vector<uint8_t> buildPoly1305Input(
        const std::vector<uint8_t>& associated_data,
        const std::vector<uint8_t>& ciphertext) {
    std::vector<uint8_t> input;
    input.reserve(
            associated_data.size() + ((16U - associated_data.size() % 16U) % 16U) +
            ciphertext.size() + ((16U - ciphertext.size() % 16U) % 16U) + 16U);
    input.insert(input.end(), associated_data.begin(), associated_data.end());
    appendPadding16(input, associated_data.size());
    input.insert(input.end(), ciphertext.begin(), ciphertext.end());
    appendPadding16(input, ciphertext.size());
    appendLittleEndian64(input, static_cast<uint64_t>(associated_data.size()));
    appendLittleEndian64(input, static_cast<uint64_t>(ciphertext.size()));
    return input;
}

std::array<uint8_t, 16> poly1305Mac(
        const std::array<uint8_t, 32>& one_time_key,
        const std::vector<uint8_t>& message) {
    constexpr uint64_t kMask26 = 0x3ffffffU;
    const uint32_t t0 = load32Le(one_time_key.data());
    const uint32_t t1 = load32Le(one_time_key.data() + 4);
    const uint32_t t2 = load32Le(one_time_key.data() + 8);
    const uint32_t t3 = load32Le(one_time_key.data() + 12);

    const uint64_t r0 = t0 & 0x3ffffffU;
    const uint64_t r1 = ((t0 >> 26U) | (t1 << 6U)) & 0x3ffff03U;
    const uint64_t r2 = ((t1 >> 20U) | (t2 << 12U)) & 0x3ffc0ffU;
    const uint64_t r3 = ((t2 >> 14U) | (t3 << 18U)) & 0x3f03fffU;
    const uint64_t r4 = (t3 >> 8U) & 0x00fffffU;

    const uint64_t s1 = r1 * 5U;
    const uint64_t s2 = r2 * 5U;
    const uint64_t s3 = r3 * 5U;
    const uint64_t s4 = r4 * 5U;

    uint64_t h0 = 0;
    uint64_t h1 = 0;
    uint64_t h2 = 0;
    uint64_t h3 = 0;
    uint64_t h4 = 0;

    size_t offset = 0;
    while (offset < message.size()) {
        std::array<uint8_t, 16> block = {};
        const size_t remaining = std::min<size_t>(16U, message.size() - offset);
        std::copy(message.begin() + static_cast<std::ptrdiff_t>(offset),
                  message.begin() + static_cast<std::ptrdiff_t>(offset + remaining),
                  block.begin());
        const uint32_t hibit = remaining == 16U ? (1U << 24U) : 0U;
        if (remaining < 16U) {
            block[remaining] = 1U;
        }

        const uint32_t b0 = load32Le(block.data());
        const uint32_t b1 = load32Le(block.data() + 4);
        const uint32_t b2 = load32Le(block.data() + 8);
        const uint32_t b3 = load32Le(block.data() + 12);

        h0 += b0 & kMask26;
        h1 += ((b0 >> 26U) | (b1 << 6U)) & kMask26;
        h2 += ((b1 >> 20U) | (b2 << 12U)) & kMask26;
        h3 += ((b2 >> 14U) | (b3 << 18U)) & kMask26;
        h4 += (b3 >> 8U) | hibit;

        uint64_t d0 = h0 * r0 + h1 * s4 + h2 * s3 + h3 * s2 + h4 * s1;
        uint64_t d1 = h0 * r1 + h1 * r0 + h2 * s4 + h3 * s3 + h4 * s2;
        uint64_t d2 = h0 * r2 + h1 * r1 + h2 * r0 + h3 * s4 + h4 * s3;
        uint64_t d3 = h0 * r3 + h1 * r2 + h2 * r1 + h3 * r0 + h4 * s4;
        uint64_t d4 = h0 * r4 + h1 * r3 + h2 * r2 + h3 * r1 + h4 * r0;

        uint64_t c = d0 >> 26U;
        h0 = d0 & kMask26;
        d1 += c;
        c = d1 >> 26U;
        h1 = d1 & kMask26;
        d2 += c;
        c = d2 >> 26U;
        h2 = d2 & kMask26;
        d3 += c;
        c = d3 >> 26U;
        h3 = d3 & kMask26;
        d4 += c;
        c = d4 >> 26U;
        h4 = d4 & kMask26;
        h0 += c * 5U;
        c = h0 >> 26U;
        h0 &= kMask26;
        h1 += c;

        offset += remaining;
    }

    uint64_t c = h1 >> 26U;
    h1 &= kMask26;
    h2 += c;
    c = h2 >> 26U;
    h2 &= kMask26;
    h3 += c;
    c = h3 >> 26U;
    h3 &= kMask26;
    h4 += c;
    c = h4 >> 26U;
    h4 &= kMask26;
    h0 += c * 5U;
    c = h0 >> 26U;
    h0 &= kMask26;
    h1 += c;

    uint64_t g0 = h0 + 5U;
    c = g0 >> 26U;
    g0 &= kMask26;
    uint64_t g1 = h1 + c;
    c = g1 >> 26U;
    g1 &= kMask26;
    uint64_t g2 = h2 + c;
    c = g2 >> 26U;
    g2 &= kMask26;
    uint64_t g3 = h3 + c;
    c = g3 >> 26U;
    g3 &= kMask26;
    const int64_t g4_signed = static_cast<int64_t>(h4 + c) - (1LL << 26U);
    const uint64_t select_h = static_cast<uint64_t>(g4_signed >> 63U);
    const uint64_t select_g = ~select_h;

    h0 = (h0 & select_h) | (g0 & select_g);
    h1 = (h1 & select_h) | (g1 & select_g);
    h2 = (h2 & select_h) | (g2 & select_g);
    h3 = (h3 & select_h) | (g3 & select_g);
    h4 = (h4 & select_h) | (static_cast<uint64_t>(g4_signed) & select_g);

    constexpr uint64_t kMask32 = 0xffffffffULL;
    const uint64_t h0_word = (h0 | (h1 << 26U)) & kMask32;
    const uint64_t h1_word = ((h1 >> 6U) | (h2 << 20U)) & kMask32;
    const uint64_t h2_word = ((h2 >> 12U) | (h3 << 14U)) & kMask32;
    const uint64_t h3_word = ((h3 >> 18U) | (h4 << 8U)) & kMask32;

    const uint64_t f0 = h0_word + load32Le(one_time_key.data() + 16);
    const uint64_t f1 = h1_word + load32Le(one_time_key.data() + 20) + (f0 >> 32U);
    const uint64_t f2 = h2_word + load32Le(one_time_key.data() + 24) + (f1 >> 32U);
    const uint64_t f3 = h3_word + load32Le(one_time_key.data() + 28) + (f2 >> 32U);

    std::array<uint8_t, 16> tag = {};
    store32Le(tag.data(), static_cast<uint32_t>(f0));
    store32Le(tag.data() + 4, static_cast<uint32_t>(f1));
    store32Le(tag.data() + 8, static_cast<uint32_t>(f2));
    store32Le(tag.data() + 12, static_cast<uint32_t>(f3));
    return tag;
}

std::array<uint8_t, kTagSize> chacha20Poly1305Tag(
        const std::array<uint8_t, 32>& key,
        const std::array<uint8_t, kNonceSize>& nonce,
        const std::vector<uint8_t>& associated_data,
        const std::vector<uint8_t>& ciphertext) {
    const auto block0 = chacha20Block(key, nonce, 0U);
    std::array<uint8_t, 32> one_time_key = {};
    std::copy(block0.begin(), block0.begin() + static_cast<std::ptrdiff_t>(one_time_key.size()), one_time_key.begin());
    return poly1305Mac(one_time_key, buildPoly1305Input(associated_data, ciphertext));
}

bool constantTimeEquals(const uint8_t* left, const uint8_t* right, size_t size) {
    uint8_t diff = 0;
    for (size_t index = 0; index < size; ++index) {
        diff = static_cast<uint8_t>(diff | (left[index] ^ right[index]));
    }
    return diff == 0;
}

std::vector<uint8_t> jbyteArrayToVector(JNIEnv* env, jbyteArray bytes) {
    if (bytes == nullptr) return {};
    const jsize length = env->GetArrayLength(bytes);
    std::vector<uint8_t> out(static_cast<size_t>(length));
    if (length > 0) {
        env->GetByteArrayRegion(bytes, 0, length, reinterpret_cast<jbyte*>(out.data()));
    }
    return out;
}

jbyteArray vectorToJbyteArray(JNIEnv* env, const std::vector<uint8_t>& bytes) {
    if (bytes.size() > static_cast<size_t>(std::numeric_limits<jsize>::max())) {
        jclass exception_class = env->FindClass("java/lang/IllegalArgumentException");
        env->ThrowNew(exception_class, "Protected payload is too large");
        return nullptr;
    }
    auto result = env->NewByteArray(static_cast<jsize>(bytes.size()));
    if (result != nullptr && !bytes.empty()) {
        env->SetByteArrayRegion(result, 0, static_cast<jsize>(bytes.size()), reinterpret_cast<const jbyte*>(bytes.data()));
    }
    return result;
}

void throwJava(JNIEnv* env, const char* class_name, const char* message) {
    jclass exception_class = env->FindClass(class_name);
    if (exception_class != nullptr) {
        env->ThrowNew(exception_class, message);
    }
}

std::vector<uint8_t> encryptBytes(JNIEnv* env, const std::vector<uint8_t>& plaintext) {
    if (!secretConfigured()) {
        throwJava(env, "java/lang/IllegalStateException", "ToolPkg protection secret is not configured");
        return {};
    }
    const auto key = deriveAeadKey();
    std::array<uint8_t, kNonceSize> nonce = {};
    fillRandom(nonce.data(), nonce.size());
    const auto ciphertext = chacha20Xor(plaintext, key, nonce);
    const auto associated_data = buildAssociatedData(nonce);
    const auto tag = chacha20Poly1305Tag(key, nonce, associated_data, ciphertext);

    std::vector<uint8_t> output;
    output.reserve(kHeaderSize + ciphertext.size());
    output.insert(output.end(), kMagic.begin(), kMagic.end());
    output.insert(output.end(), nonce.begin(), nonce.end());
    output.insert(output.end(), tag.begin(), tag.end());
    output.insert(output.end(), ciphertext.begin(), ciphertext.end());
    return output;
}

std::vector<uint8_t> decryptBytes(JNIEnv* env, const std::vector<uint8_t>& protected_bytes) {
    if (!secretConfigured()) {
        throwJava(env, "java/lang/IllegalStateException", "ToolPkg protection secret is not configured");
        return {};
    }
    if (protected_bytes.size() < kHeaderSize ||
        !std::equal(kMagic.begin(), kMagic.end(), protected_bytes.begin())) {
        throwJava(env, "java/lang/IllegalArgumentException", "Not an Operit protected payload");
        return {};
    }

    std::array<uint8_t, kNonceSize> nonce = {};
    std::copy(
            protected_bytes.begin() + static_cast<std::ptrdiff_t>(kMagic.size()),
            protected_bytes.begin() + static_cast<std::ptrdiff_t>(kMagic.size() + nonce.size()),
            nonce.begin());
    const uint8_t* provided_tag = protected_bytes.data() + kMagic.size() + nonce.size();
    std::vector<uint8_t> ciphertext(
            protected_bytes.begin() + static_cast<std::ptrdiff_t>(kHeaderSize),
            protected_bytes.end());
    const auto key = deriveAeadKey();
    const auto associated_data = buildAssociatedData(nonce);
    const auto expected_tag = chacha20Poly1305Tag(key, nonce, associated_data, ciphertext);
    if (!constantTimeEquals(provided_tag, expected_tag.data(), expected_tag.size())) {
        throwJava(env, "java/lang/SecurityException", "Protected payload authentication failed");
        return {};
    }
    return chacha20Xor(ciphertext, key, nonce);
}

}  // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_com_ai_assistance_operit_util_ToolPkgProtectionNative_isSecretConfigured(JNIEnv*, jobject) {
    return secretConfigured() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_ai_assistance_operit_util_ToolPkgProtectionNative_encrypt(JNIEnv* env, jobject, jbyteArray bytes) {
    const auto plaintext = jbyteArrayToVector(env, bytes);
    if (env->ExceptionCheck()) return nullptr;
    const auto encrypted = encryptBytes(env, plaintext);
    if (env->ExceptionCheck()) return nullptr;
    return vectorToJbyteArray(env, encrypted);
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_ai_assistance_operit_util_ToolPkgProtectionNative_decrypt(JNIEnv* env, jobject, jbyteArray bytes) {
    const auto protected_bytes = jbyteArrayToVector(env, bytes);
    if (env->ExceptionCheck()) return nullptr;
    const auto decrypted = decryptBytes(env, protected_bytes);
    if (env->ExceptionCheck()) return nullptr;
    return vectorToJbyteArray(env, decrypted);
}
