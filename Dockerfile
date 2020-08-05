FROM golang:1.14-buster as build

WORKDIR /go/src/app
ADD go.mod go.sum ./

RUN go mod download -x

COPY . .

ENV GOOS=linux
RUN go build -tags 'osusergo netgo' -o /go/bin/app 

FROM gcr.io/distroless/static-debian10
COPY --from=build /go/bin/app /
ENTRYPOINT ["/app"]
