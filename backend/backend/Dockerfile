FROM python:3.11-slim

ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1

WORKDIR /app

# System deps (minimal)
RUN pip install --no-cache-dir poetry==1.8.3

COPY pyproject.toml /app/pyproject.toml
# No lock in demo; Poetry will resolve
RUN poetry config virtualenvs.create false  && poetry install --no-interaction --no-ansi

COPY app /app/app

EXPOSE 8000
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
