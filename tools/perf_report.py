"""Summarize only JccPerf logs; distinguish refresh-only cycles from cycles containing buys."""
from pathlib import Path
import json
import re
import statistics
import sys

def summary(values):
    if not values:
        return {'count': 0}
    return {'count': len(values), 'min_ms': min(values), 'median_ms': statistics.median(values), 'max_ms': max(values)}

def main():
    text = Path(sys.argv[1] if len(sys.argv) > 1 else '.tools/perf.log').read_text(encoding='utf-8-sig')
    groups = {}; changed = []; cached = []; frame_ages = []
    empty_gaps = []; buying_gaps = []; bought_between = False; pending_gap_group = None
    for line in text.splitlines():
        explicit_gap = re.search(r'refresh_gap_ms=(\d+)', line)
        if explicit_gap and pending_gap_group is not None:
            pending_gap_group.append(int(explicit_gap[1])); pending_gap_group = None
        frame = re.search(r'frame_ms=(\d+) ocr_ms=(\d+) cached=(true|false)', line)
        if frame:
            frame_ages.append(int(frame[1]))
            (cached if frame[3] == 'true' else changed).append(int(frame[2]))
        action = re.search(r'action=(BUY|REFRESH) count=(\d+) scheduled_ms=(\d+) actual_ms=(\d+)', line)
        if not action:
            continue
        key = action[1] + '_' + action[2]
        groups.setdefault(key, []).append(int(action[4]))
        if action[1] == 'BUY':
            bought_between = True
        else:
            # The app emits this explicit gap only within a continuous enabled session.
            pending_gap_group = buying_gaps if bought_between else empty_gaps
            bought_between = False
    print(json.dumps({'actions': {k: summary(v) for k, v in groups.items()},
        'ocr_changed': summary(changed), 'ocr_cached': summary(cached), 'frame_age': summary(frame_ages),
        'frames_over_180_ms': sum(x > 180 for x in frame_ages),
        'refresh_without_buy': summary(empty_gaps), 'refresh_with_buys': summary(buying_gaps)}, indent=2))

if __name__ == '__main__':
    main()
