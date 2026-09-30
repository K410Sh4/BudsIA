#pragma once

#include <algorithm>
#include <atomic>
#include <cstddef>
#include <cstdint>
#include <type_traits>

namespace budsia::audio {

template <typename T, std::size_t Capacity>
class LockFreeSpscRingBuffer {
    static_assert(std::is_trivially_copyable_v<T>);
    static_assert(Capacity >= 2);
    static_assert((Capacity & (Capacity - 1)) == 0,
                  "Capacity must be a power of two.");

public:
    [[nodiscard]] std::size_t availableToRead() const noexcept {
        const auto write = writeIndex_.load(std::memory_order_acquire);
        const auto read = readIndex_.load(std::memory_order_acquire);
        return static_cast<std::size_t>(write - read);
    }

    [[nodiscard]] std::size_t availableToWrite() const noexcept {
        return Capacity - availableToRead();
    }

    std::size_t write(const T* source, std::size_t count) noexcept {
        if (source == nullptr || count == 0) return 0;

        const auto write = writeIndex_.load(std::memory_order_relaxed);
        const auto read = readIndex_.load(std::memory_order_acquire);
        const auto used = static_cast<std::size_t>(write - read);
        const auto writable = std::min(count, Capacity - used);

        for (std::size_t i = 0; i < writable; ++i) {
            storage_[(write + i) & kMask] = source[i];
        }

        writeIndex_.store(write + writable, std::memory_order_release);
        return writable;
    }

    std::size_t read(T* destination, std::size_t count) noexcept {
        if (destination == nullptr || count == 0) return 0;

        const auto read = readIndex_.load(std::memory_order_relaxed);
        const auto write = writeIndex_.load(std::memory_order_acquire);
        const auto readable = std::min(
            count,
            static_cast<std::size_t>(write - read)
        );

        for (std::size_t i = 0; i < readable; ++i) {
            destination[i] = storage_[(read + i) & kMask];
        }

        readIndex_.store(read + readable, std::memory_order_release);
        return readable;
    }

    void clear() noexcept {
        const auto write = writeIndex_.load(std::memory_order_acquire);
        readIndex_.store(write, std::memory_order_release);
    }

private:
    static constexpr std::size_t kMask = Capacity - 1;

    alignas(64) T storage_[Capacity]{};
    alignas(64) std::atomic<std::uint64_t> writeIndex_{0};
    alignas(64) std::atomic<std::uint64_t> readIndex_{0};
};

}  // namespace budsia::audio
