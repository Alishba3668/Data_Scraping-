import os
import re
import requests
import threading
from bs4 import BeautifulSoup
from concurrent.futures import ThreadPoolExecutor

THREAD_COUNT = 50
MAX_RETRIES = 3
TIMEOUT = 60

BASE_URL = "https://papers.nips.cc"
OUTPUT_DIR = "E:/Scrapped_Data_Python/"

def extract_year(url):
    match = re.search(r'\d{4}', url)
    return match.group(0) if match else "unknown"

def sanitize_filename(filename):
    return re.sub(r'[\\/:*?"<>|]', '_', filename)

def download_pdf(pdf_url, output_dir, file_name):
    try:
        os.makedirs(output_dir, exist_ok=True)
        response = requests.get(pdf_url, stream=True, timeout=TIMEOUT)
        response.raise_for_status()
        pdf_path = os.path.join(output_dir, f"{file_name}.pdf")
        with open(pdf_path, 'wb') as f:
            for chunk in response.iter_content(chunk_size=8192):
                f.write(chunk)
    except requests.RequestException as e:
        print(f"Failed to download {pdf_url}: {e}")

def process_paper(paper_url, year_output_dir):
    attempts = 0
    while attempts < MAX_RETRIES:
        try:
            response = requests.get(paper_url, timeout=TIMEOUT)
            response.raise_for_status()
            soup = BeautifulSoup(response.text, 'html.parser')
            paper_title = sanitize_filename(soup.title.string if soup.title else "unknown")
            pdf_link = soup.select_one("a[href$='Paper-Conference.pdf']")
            if pdf_link:
                pdf_url = BASE_URL + pdf_link['href']
                download_pdf(pdf_url, year_output_dir, paper_title)
            return
        except requests.RequestException:
            attempts += 1
    print(f"Giving up on {paper_url}")

def scrape_pdfs():
    try:
        response = requests.get(BASE_URL, timeout=TIMEOUT)
        response.raise_for_status()
        soup = BeautifulSoup(response.text, 'html.parser')
        year_links = soup.select("a[href^='/paper_files/paper/']")

        with ThreadPoolExecutor(max_workers=THREAD_COUNT) as executor:
            for year_link in year_links:
                year_url = BASE_URL + year_link['href']
                year = extract_year(year_url)
                year_output_dir = os.path.join(OUTPUT_DIR, year)
                os.makedirs(year_output_dir, exist_ok=True)
                
                try:
                    response = requests.get(year_url, timeout=TIMEOUT)
                    response.raise_for_status()
                    year_soup = BeautifulSoup(response.text, 'html.parser')
                    paper_links = year_soup.select("ul.paper-list li a[href$='Abstract-Conference.html']")
                    for paper_link in paper_links:
                        paper_url = BASE_URL + paper_link['href']
                        executor.submit(process_paper, paper_url, year_output_dir)
                except requests.RequestException:
                    print(f"Failed to process year: {year_url}")
    except requests.RequestException:
        print("An error occurred during the scraping process.")

if __name__ == "__main__":
    scrape_pdfs()
