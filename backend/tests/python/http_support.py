"""Shared cookie transport for API and rendered-form checks."""
import http.cookiejar
import urllib.error
import urllib.request


def opener(*handlers):
    return urllib.request.build_opener(
        urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()), *handlers)


def send(http, request):
    try:
        response = http.open(request)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        return response.status, response.headers, response.read()
