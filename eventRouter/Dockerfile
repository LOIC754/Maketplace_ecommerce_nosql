# Image Python légère
FROM python:3.12-slim
WORKDIR /app
# Dépendances d'abord : couche mise en cache tant que requirements.txt ne change pas
COPY eventRouter/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY eventRouter/router.py .
# -u : logs affichés immédiatement (sans mise en mémoire tampon)
CMD ["python", "-u", "router.py"]
