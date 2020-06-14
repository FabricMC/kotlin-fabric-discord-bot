package utils

import (
	"errors"
	"fmt"
	"os"
)

func GetEnv(key string) (string, error) {
	token := os.Getenv(key)

	if len(token) == 0 {
		return "", errors.New(fmt.Sprintf("could not find %s env variable", key))
	}

	return token, nil
}
