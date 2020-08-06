package versioncheck

import (
	"bytes"
	"net/http"
	"time"
	"unsafe"
)

var httpClient = &http.Client{Timeout: 10 * time.Second}

func getJson(url string) (string, error) {
	r, err := httpClient.Get(url)
	if err != nil {
		return "", err
	}
	defer r.Body.Close()

	var buf = new(bytes.Buffer)
	buf.ReadFrom(r.Body)
	var b = buf.Bytes()
	var s = *(*string)(unsafe.Pointer(&b))
	return s, nil
}

func contains(s []string, e string) bool {
	for _, a := range s {
		if a == e {
			return true
		}
	}
	return false
}
