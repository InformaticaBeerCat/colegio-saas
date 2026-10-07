# Respaldo diario (SEG-05): mysqldump de la misma versión que la base, más tar y gzip.
FROM mysql:8.4
RUN microdnf install -y tar gzip findutils && microdnf clean all
COPY respaldar.sh restaurar.sh programar.sh /usr/local/bin/
RUN chmod +x /usr/local/bin/respaldar.sh /usr/local/bin/restaurar.sh /usr/local/bin/programar.sh
ENTRYPOINT ["/usr/local/bin/programar.sh"]
