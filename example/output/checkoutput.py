import re
import sys

#ex usage python3 checkoutput.py 3.output ../configs/perfect-links.config 3
# output_file Config_file num_of_hosts


def parse_config(config_file):
    """Parse the config file to get the number of messages (m) and the receiver ID."""
    with open(config_file, 'r') as f:
        first_line = f.readline().strip()
        m, receiver_id = map(int, first_line.split())
    return m, receiver_id

def check_receiver_output(output_file, config_file, total_processes):
    """Check if the receiver received all messages from all senders."""
    # Parse the config file to get number of messages (m)
    m, receiver_id = parse_config(config_file)

    # Store which messages have been delivered
    delivered_messages = {}

    # Parse the output file of the receiver
    with open(output_file, 'r') as f:
        lines = f.readlines()

    # Go through the lines to collect the delivered messages
    for line in lines:
        line = line.strip()
        match = re.match(r"^d (\d+) (\d+)$", line)
        if match:
            sender_id = int(match.group(1))
            seq_num = int(match.group(2))
            if sender_id not in delivered_messages:
                delivered_messages[sender_id] = set()
            delivered_messages[sender_id].add(seq_num)

    # Check for missing messages
    all_received = True
    for sender_id in range(1, total_processes + 1):
        if sender_id == receiver_id:
            continue  # Skip the receiver itself

        # Check if all messages from this sender were received
        for seq_num in range(1, m + 1):
            if sender_id not in delivered_messages or seq_num not in delivered_messages[sender_id]:
                print(f"Missing message from sender {sender_id}: message {seq_num}")
                all_received = False

    if all_received:
        print(f"{output_file}: All messages were correctly received.")
    else:
        print(f"{output_file}: Some messages are missing.")

if __name__ == "__main__":
    if len(sys.argv) < 4:
        print("Usage: python check_receiver.py <output_file> <config_file> <total_processes>")
    else:
        check_receiver_output(sys.argv[1], sys.argv[2], int(sys.argv[3]))
