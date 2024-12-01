import re
import sys
from collections import defaultdict

#python3 check_fifo_output.py <output_file> <config_file> <total_processes> <process_id>

# output_file config_file num_of_processes

def parse_config(config_file):
    """Parse the config file to get the number of messages (m)."""
    with open(config_file, 'r') as f:
        first_line = f.readline().strip()
        m = int(first_line)
    return m

def check_fifo_output(output_file, config_file, total_processes, process_id):
    """Check if the process delivers messages in FIFO order and receives all messages."""
    # Parse the config file to get the number of messages (m)
    m = parse_config(config_file)

    # Store delivered messages
    delivered_messages = defaultdict(list)

    # Parse the output file of the process
    with open(output_file, 'r') as f:
        lines = f.readlines()

    # Go through the lines to collect delivered messages
    for line in lines:
        line = line.strip()
        if line.startswith('d'):  # Delivery line
            match = re.match(r"^d (\d+) (\d+)$", line)
            if match:
                sender_id = int(match.group(1))
                seq_num = int(match.group(2))
                delivered_messages[sender_id].append(seq_num)

    # Check FIFO property and completeness
    all_correct = True

    for sender_id in range(1, total_processes + 1):
        if sender_id == process_id:
            continue  # Skip self-check for broadcast messages

        # Verify FIFO order
        delivered_seq = delivered_messages[sender_id]
        if delivered_seq != sorted(delivered_seq):
            print(f"{output_file}: Messages from sender {sender_id} were not delivered in FIFO order.")
            print(f"  Delivered sequence: {delivered_seq}")
            all_correct = False

        # Verify all messages were delivered
        expected_seq = set(range(1, m + 1))
        delivered_seq_set = set(delivered_seq)
        missing = expected_seq - delivered_seq_set
        if missing:
            print(f"{output_file}: Missing messages from sender {sender_id}: {sorted(missing)}")
            all_correct = False

    if all_correct:
        print(f"{output_file}: All messages were delivered in FIFO order and none are missing.")
    else:
        print(f"{output_file}: Some messages are missing or out of order.")

if __name__ == "__main__":
    if len(sys.argv) < 5:
        print("Usage: python check_fifo_output.py <output_file> <config_file> <total_processes> <process_id>")
    else:
        check_fifo_output(sys.argv[1], sys.argv[2], int(sys.argv[3]), int(sys.argv[4]))
