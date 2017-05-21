FROM java:openjdk-8u111

# Install Proj4
# Setup build env
RUN mkdir /build
RUN apt-key adv --recv-keys --keyserver hkp://keyserver.ubuntu.com:80 16126D3A3E5C1192    \
    && apt-get update \
    && apt-get install -y --fix-missing --no-install-recommends \
            software-properties-common build-essential ca-certificates \
            git make cmake wget unzip libtool automake python-pip \
            libpython-dev libjpeg-dev zlib1g-dev \
            python-dev python-pip g++ doxygen dvipng \
            cmake libjpeg62-turbo-dev zlib1g-dev texlive-latex-base \
            texlive-latex-extra git \
            graphviz python-matplotlib \
            python-setuptools imagemagick \
    && apt-get remove --purge -y $BUILD_PACKAGES  && rm -rf /var/lib/apt/lists/*

RUN pip install Sphinx breathe \
    sphinx_bootstrap_theme awscli sphinxcontrib-bibtex \
    sphinx_rtd_theme

RUN export JAVA_HOME=$(readlink -f /usr/bin/javac | sed "s:/bin/javac::")

RUN git clone https://github.com/OSGeo/proj.4.git \
    && cd proj.4 \
    && ./autogen.sh \
    && CFLAGS=-I$JAVA_HOME/include/linux ./configure --with-jni=$JAVA_HOME/include --prefix=/usr \
    && make \
    && make install

COPY ./build/install /opt/src/geometry-service-java

WORKDIR /opt/src/geometry-service-java

RUN chmod +x /opt/src/geometry-service-java/geometry-service/bin/geometry-operators-server

EXPOSE 8980

CMD ["/opt/src/geometry-service-java/geometry-service/bin/geometry-operators-server"]