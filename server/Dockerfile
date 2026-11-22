FROM node:22-alpine
WORKDIR /app
COPY server.js .
ENV PORT=8080 DB_PATH=/data/codes.db
VOLUME /data
EXPOSE 8080
CMD ["node", "server.js"]
