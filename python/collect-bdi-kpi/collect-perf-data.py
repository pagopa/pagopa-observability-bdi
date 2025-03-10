import os
import sys
import argparse
import requests
from datetime import datetime, timedelta
import json
from urllib.parse import quote

# read configuration
API_URL = os.getenv("API_URL")
API_KEY = os.getenv('API_KEY')

if not API_KEY:
    print("❌ Error: API_KEY not present")
    sys.exit(1)

def parse_args():
    parser = argparse.ArgumentParser(description='Script to invoke KPI API')
    parser.add_argument('--kpi_id', type=str, default='ALL', help='KPI ID (default: ALL)')
    parser.add_argument('--start_date', type=str, help='Start date (format: YYYY-MM-DD HH:MM:SS)')
    parser.add_argument('--end_date', type=str, help='End fine (format: YYYY-MM-DD HH:MM:SS)')
    return parser.parse_args()

def get_default_dates():
    now = datetime.now()
    yesterday = now - timedelta(days=1)
    start_date = datetime(yesterday.year, yesterday.month, yesterday.day, 0, 0, 0)
    end_date = datetime(yesterday.year, yesterday.month, yesterday.day, 23, 59, 59)
    return start_date, end_date

def call_api(kpi_id, start, end):
    
    # set query params
    start_str = quote(start.strftime('%Y-%m-%d %H:%M:%S'))
    end_str = quote(end.strftime('%Y-%m-%d %H:%M:%S'))
    
    url = f"{API_URL}?startDate={start_str}&endDate={end_str}&kpiId={kpi_id}"
    
    headers = {
        'Ocp-Apim-Subscription-Key': API_KEY
    }
    
    try:
        response = requests.post(url, headers=headers)
        
        if response.status_code == 200:
            print(f"✅ SUCCESS | Interval: {start} - {end}")
            return True
        else:
            print(f"❌ ERROR | Interval: {start} - {end} | Status Code: {response.status_code}")
            return False
    except Exception as e:
        print(f"❌ EXCEPTION | Interval: {start} - {end} | Error: {str(e)}")
        return False

def main():
    print("parsing arguments...")
    args = parse_args()

    # manage start_date and end_date
    print(f"inmput arguments: kpi_id[{args.kpi_id}] sart_date[{args.start_date}] end_date[{args.end_date}]")
    if args.start_date and args.start_date != "N/A":
        start_date = datetime.strptime(args.start_date, '%Y-%m-%dT%H:%M:%S')
    else:
        start_date, _ = get_default_dates()

    if args.end_date and args.end_date != "N/A":
        end_date = datetime.strptime(args.end_date, '%Y-%m-%dT%H:%M:%S')
    else:
        _, end_date = get_default_dates()

    kpi_id = args.kpi_id
    
    print(f"calculated arguments: kpi_id[{kpi_id}] sart_date[{start_date}] end_date[{end_date}]")

    # initialize payload JSON
    payload = {
        "text": "✅ *DBI Observability - data collection report*",
        "blocks": []
    }

    # initialize data interval
    current_start = start_date
    delta = timedelta(hours=3)
    while current_start < end_date:
        
        # set end date
        current_end = min(current_start + delta, end_date)

        print(f"calling api: kpi_id[{kpi_id}] sart_date[{current_start}] end_date[{current_end}]")
        success = call_api(kpi_id, current_start, current_end)

        # update payload block
        print(f"updating payload block success[{success}]")
        interval_label = f"{current_start.strftime('%Y-%m-%d %H:%M:%S')} - {current_end.strftime('%Y-%m-%d %H:%M:%S')}"
        if success:
            block = {
                "type": "section",
                "text": {
                    "type": "mrkdwn",
                    "text": f"✅ *Intervallo - {interval_label} - eseguito con successo*"
                }
            }
        else:
            block = {
                "type": "section",
                "text": {
                    "type": "mrkdwn",
                    "text": f"❌ *Intervallo - {interval_label} - errore durante il campionamento*"
                }
            }

        payload["blocks"].append(block)

        # next interval
        current_start = current_end

    # write payload
    print("write payload.json")
    with open('payload.json', 'w') as outfile:
        json.dump(payload, outfile, indent=2)

    print("✅ payload.json correctly created")

if __name__ == '__main__':
    main()
