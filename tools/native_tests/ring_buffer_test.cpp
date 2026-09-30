#include <array>
#include <cassert>
#include <cstddef>
#include <iostream>

#include "../../app/src/main/cpp/LockFreeSpscRingBuffer.h"

using budsia::audio::LockFreeSpscRingBuffer;

int main() {
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

    std::cout << "ring_buffer_test: PASS\n";
    return 0;
}
