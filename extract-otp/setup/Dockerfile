FROM google/cloud-sdk:latest

WORKDIR /setup

COPY gcp_setup.sh .

RUN chmod +x gcp_setup.sh

ENTRYPOINT ["bash", "gcp_setup.sh"]
