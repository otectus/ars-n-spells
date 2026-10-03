"""Isolated IEEE-754 probe of ANS's observed-debit check, not a Minecraft test.

Models the float narrowing in ArsNativeBridge.getMana(), the before/after
measurement in CastLedger.BridgeResourceAccess.debit(), and the comparison in
NativePayment.settle(). The modeled native Ars subtraction uses doubles.
"""
from __future__ import annotations

import json
import struct
from pathlib import Path


def float32(value: float) -> float:
    return struct.unpack('!f', struct.pack('!f', value))[0]


def float32_ulp(value: float) -> float:
    value = float32(abs(value))
    bits = struct.unpack('!I', struct.pack('!f', value))[0]
    following = struct.unpack('!f', struct.pack('!I', bits + 1))[0]
    return following - value


def probe(balance: float, quoted_cost: float) -> dict[str, float | bool]:
    native_after = balance - float32(quoted_cost)
    observed_before = float32(balance)
    observed_after = float32(native_after)
    measured = max(0.0, observed_before - observed_after)
    threshold = max(0.001, 2.0 * float32_ulp(quoted_cost))
    return {
        'native_balance_before': balance,
        'native_balance_after': native_after,
        'quoted_cost': quoted_cost,
        'native_debit': balance - native_after,
        'observed_before': observed_before,
        'observed_after': observed_after,
        'measured_debit': measured,
        'existing_threshold': threshold,
        'existing_check_rejects': abs(measured - quoted_cost) > threshold,
    }


def main() -> None:
    cases = [probe(1000.0, 20.0), probe(32768.001, 1.0), probe(65536.002, 1.0)]
    assert cases[0]['existing_check_rejects'] is False
    assert cases[1]['native_debit'] == 1.0
    assert cases[1]['existing_check_rejects'] is True
    assert cases[2]['native_debit'] == 1.0
    assert cases[2]['existing_check_rejects'] is True
    output = {
        'scope': 'Isolated arithmetic model only; does not launch or test Minecraft.',
        'result': 'Assertions passed: correct double-backed debits can be rejected after float-narrowed reads.',
        'cases': cases,
    }
    text = json.dumps(output, indent=2)
    Path(__file__).with_name('payment_boundary_probe_results.json').write_text(text + '\n', encoding='utf-8')
    print(text)


if __name__ == '__main__':
    main()
