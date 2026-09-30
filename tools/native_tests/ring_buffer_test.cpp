#include <array>
#include <atomic>
#include <cassert>
#include <cstddef>
#include <cstdint>
#include <iostream>
#include <thread>

#include "../../app/src/main/cpp/LockFreeSpscRingBuffer.h"

using budsia::audio::LockFreeSpscRingBuffer;

namespace {

void sequentialWrapAroundTest() {
    LockFreeSpscRingBuffer<float, 8> ring;

    const std::array<float, 6> first{1, 2, 3, 4, 5, 6};
    assert(ring.write(first.data(), first.size()) == 6);
    assert(ring.availableToRead() == 6);
    assert(ring.availableToWrite() == 2);

    std::array<float, 4> readA{};
    assert(ring.read(readA.data(), readA.size()) == 4);
    assert((readA == std::array<float, 4>{1, 2, 3, 4}));

    const std::array<float, 6> second{7, 8, 9, 10, 11, 12};
    assert(ring.write(second.data(), second.size()) == 6);
    assert(ring.availableToRead() == 8);

    std::array<float, 8> readB{};
    assert(ring.read(readB.data(), readB.size()) == 8);
    assert((readB == std::array<float, 8>{5, 6, 7, 8, 9, 10, 11, 12}));

    ring.write(first.data(), first.size());
    ring.clear();
    assert(ring.availableToRead() == 0);
}

void concurrentSpscOrderingTest() {
    constexpr std::uint64_t kItemCount = 250'000;
    LockFreeSpscRingBuffer<std::uint64_t, 1024> ring;
    std::atomic<bool> producerDone{false};

    std::thread producer([&] {
        for (std::uint64_t value = 1; value <= kItemCount; ++value) {
            while (ring.write(&value, 1) != 1) {
                std::this_thread::yield();
            }
        }
        producerDone.store(true, std::memory_order_release);
    });

    std::uint64_t expected = 1;
    while (
        expected <= kItemCount ||
        !producerDone.load(std::memory_order_acquire)
    ) {
        std::uint64_t value = 0;
        if (ring.read(&value, 1) == 1) {
            assert(value == expected);
            ++expected;
        } else {
            std::this_thread::yield();
        }
    }

    producer.join();
    assert(expected == kItemCount + 1);
    assert(ring.availableToRead() == 0);
}

}  // namespace

int main() {
    sequentialWrapAroundTest();
    concurrentSpscOrderingTest();

    std::cout << "ring_buffer_test: PASS\n";
    return 0;
}
