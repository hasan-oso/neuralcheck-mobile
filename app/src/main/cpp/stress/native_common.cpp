#include "stress/native_common.h"

namespace neuralcheck::stress {

std::atomic<bool>& native_verify_cancel_flag() noexcept {
    static std::atomic<bool> flag{false};
    return flag;
}

}  // namespace neuralcheck::stress
