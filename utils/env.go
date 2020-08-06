package utils

import (
	"fmt"
	"os"
)

func GetEnv(key string) (string, error) {
	token := os.Getenv(key)

	if len(token) == 0 {
		return "", fmt.Errorf("could not find %s env variable", key)
	}

	return token, nil
}

func HasEnv(key string) bool {
	token := os.Getenv(key)
	return len(token) != 0
}
