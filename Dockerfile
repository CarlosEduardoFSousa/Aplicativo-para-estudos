FROM php:8.3-apache

RUN apt-get update \
    && apt-get install -y --no-install-recommends libcurl4-openssl-dev libonig-dev \
    && docker-php-ext-install curl mbstring mysqli \
    && rm -rf /var/lib/apt/lists/*

COPY docker/apache-security.conf /etc/apache2/conf-available/academia-security.conf
RUN a2enconf academia-security

COPY php_appest/ /var/www/html/php_appest/
COPY docker/entrypoint.sh /usr/local/bin/academia-entrypoint
RUN chmod +x /usr/local/bin/academia-entrypoint \
    && chown -R www-data:www-data /var/www/html

ENV APP_ENV=production
EXPOSE 8080
ENTRYPOINT ["/usr/local/bin/academia-entrypoint"]

