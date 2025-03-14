import os
import sys
import json
import requests
import datetime

def main(year, quarter):
    # Recupera le variabili di ambiente
    api_url = os.getenv('API_URL')
    api_key = os.getenv('API_KEY')

    if not api_url or not api_key:
        print("⚠️  Error: API_URL and/or API_KEY not correctly configured")
        sys.exit(1)

    # build url
    endpoint = f"{api_url}/quarter/{quarter}?year={year}"
    headers = {
        'Ocp-Apim-Subscription-Key': api_key
    }

    try:
        # Emake the post
        response = requests.get(endpoint, headers=headers)

        # manage response payload
        if response.status_code == 200:
            message = {
                "text": "✅ *DBI Observability - Send Quarter Data report*",
                "blocks": [
                    {
                        "type": "section",
                        "text": {
                            "type": "mrkdwn",
                            "text": f"✅ *I dati del quarter {quarter} relativi all'anno {year} sono stati inviati con successo*"
                        }
                    }
                ]
            }
        else:
            message = {
                "text": "✅ *DBI Observability - Send Quarter Data report*",
                "blocks": [
                    {
                        "type": "section",
                        "text": {
                            "type": "mrkdwn",
                            "text": f"❌ *Si sono verificati problemi durante l'invio dei dati del quarter {quarter} relativi all'anno {year}*"
                        }
                    }
                ]
            }

        # write down the payload
        with open('payload.json', 'w', encoding='utf-8') as f:
            json.dump(message, f, ensure_ascii=False, indent=4)

        print("✅ Payload stored in 'payload.json'.")

    except Exception as e:
        print(f"❌ Error while invoking the API: {e}")
        sys.exit(1)

if __name__ == '__main__':
    if len(sys.argv) != 5:
        print("Usage: python send-quarter-data.py <year> <quarter>")
        sys.exit(1)

    input_year = sys.argv[2]
    input_quarter = sys.argv[4].upper()

    if input_quarter not in ['Q1', 'Q2', 'Q3', 'Q4', 'LAST']:
        print("⚠️  Error: The parameter quarter must be one of 'Q1', 'Q2', 'Q3', 'Q4', 'LAST'")
        sys.exit(1)

    if input_year == "N/A":
        current_year = datetime.now().year
        input_year = str(current_year) 

    main(input_year, input_quarter)
    