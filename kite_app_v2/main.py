from fastapi import FastAPI

app = FastAPI()

@app.get("/status")
def start():
    return "hello world"


@app.
def login():
    return None