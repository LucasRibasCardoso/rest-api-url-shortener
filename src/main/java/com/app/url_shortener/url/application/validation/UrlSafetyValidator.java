package com.app.url_shortener.url.application.validation;

import com.app.url_shortener.url.domain.exception.UnsafeUrlException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Política determinística de destino de URL para o fluxo público de encurtamento.
 *
 * <p>Este validador não substitui o validador sintático de HTTP/HTTPS da camada de presentation.
 * Ele assume que a URL já foi verificada como uma URL HTTP bem formada e decide se o host de
 * destino é aceitável para um encurtador público.
 *
 * <p>A política evita resolução DNS de forma intencional. Hostnames são avaliados apenas pelo texto
 * literal, e {@link InetAddress} é usado somente para candidatos IPv6 porque IPv6 possui muitas
 * formas textuais válidas que são fáceis de analisar incorretamente de forma manual.
 */
@Component
public class UrlSafetyValidator {

  private static final Set<String> INTERNAL_DOMAIN_SUFFIXES =
      Set.of(".local", ".internal", ".lan", ".home", ".corp");

  public void validate(String originalUrl) {
    URI uri = parseUri(originalUrl);
    String host = normalizeHost(extractHost(uri));

    if (isBlockedHost(host)) {
      throw new UnsafeUrlException();
    }
  }

  private URI parseUri(String originalUrl) {
    try {
      return URI.create(originalUrl.trim());
    } catch (IllegalArgumentException | NullPointerException e) {
      throw new UnsafeUrlException();
    }
  }

  private String normalizeHost(String host) {
    String normalizedHost = host.toLowerCase(Locale.ROOT);

    // [::1] -> ::1
    if (normalizedHost.startsWith("[") && normalizedHost.endsWith("]")) {
      normalizedHost = normalizedHost.substring(1, normalizedHost.length() - 1);
    }

    // example.com..... -> example.com
    while (normalizedHost.endsWith(".")) {
      normalizedHost = normalizedHost.substring(0, normalizedHost.length() - 1);
    }

    return normalizedHost;
  }

  private String extractHost(URI uri) {
    String host = uri.getHost();

    if (host == null || host.isBlank()) {
      throw new UnsafeUrlException();
    }

    return host;
  }

  private boolean isBlockedHost(String host) {
    return isLocalhost(host)
        || isSingleLabelHost(host)
        || hasInternalDomainSuffix(host)
        || isInternationalizedDomainName(host)
        || isBlockedIpLiteral(host);
  }

  private boolean isLocalhost(String host) {
    // Bloqueia 'localhost' e '*.localhost' para evitar destinos de loopback locais.
    return host.equals("localhost") || host.endsWith(".localhost");
  }

  private boolean isSingleLabelHost(String host) {
    // Bloqueia 'admin', 'example'...
    return !host.contains(".") && !isIpLiteral(host);
  }

  private boolean hasInternalDomainSuffix(String host) {
    // Bloquei sufixos DNS privados/internos comuns em redes locais e ambientes corporativos.
    for (String suffix : INTERNAL_DOMAIN_SUFFIXES) {
      if (host.endsWith(suffix)) {
        return true;
      }
    }

    return false;
  }

  private boolean isInternationalizedDomainName(String host) {
    // Bloqueia hosts como 'exâmple.com' e 'xn--exmple-cua.com'...
    return hasNonAsciiCharacter(host) || hasPunycodeLabel(host);
  }

  private boolean hasNonAsciiCharacter(String host) {
    // Detecta hostnames unicode diretos como: 'exâmple.com'
    for (int index = 0; index < host.length(); index++) {
      if (host.charAt(index) > 0x7F) {
        return true;
      }
    }

    return false;
  }

  private boolean hasPunycodeLabel(String host) {
    // Detecta labels punycode apenas no início de labels, por exemplo xn--example ou
    // www.xn--example.
    for (String label : host.split("\\.")) {
      if (label.startsWith("xn--")) {
        return true;
      }
    }

    return false;
  }

  private boolean isBlockedIpLiteral(String host) {
    // Bloqueia literais IPv4 e IPv6 inseguros sem resolver nomes de domínio.
    return isBlockedIpv4Literal(host) || isBlockedIpv6Literal(host);
  }

  private boolean isIpLiteral(String host) {
    // Detecta se o host normalizado é um literal IPv4 estrito ou um literal IPv6 válido.
    return parseIpv4Literal(host) != null || parseIpv6Literal(host) != null;
  }

  /**
   * Bloqueia literais IPv4 em faixas privadas, loopback, link-local, metadata, unspecified e
   * multicast. Valores que parecem IPv4, mas falham no parsing estrito, também são bloqueados para
   * evitar interpretação ambígua em componentes posteriores.
   */
  private boolean isBlockedIpv4Literal(String host) {
    if (looksLikeIpv4Literal(host) && parseIpv4Literal(host) == null) {
      return true;
    }

    int[] octets = parseIpv4Literal(host);

    if (octets == null) {
      return false;
    }

    return octets[0] == 0
        || octets[0] == 10
        || octets[0] == 127
        || (octets[0] == 169 && octets[1] == 254)
        || (octets[0] == 172 && octets[1] >= 16 && octets[1] <= 31)
        || (octets[0] == 192 && octets[1] == 168)
        || (octets[0] >= 224 && octets[0] <= 239);
  }

  /**
   * Faz parsing apenas de IPv4 decimal pontuado canônico com exatamente quatro octetos. Notações
   * alternativas, octetos vazios, valores fora de 0-255 e octetos com zero à esquerda são
   * rejeitados.
   */
  private int[] parseIpv4Literal(String host) {
    String[] parts = host.split("\\.", -1);

    if (parts.length != 4) {
      return null;
    }

    int[] octets = new int[4];

    for (int index = 0; index < parts.length; index++) {
      String part = parts[index];

      if (part.isEmpty() || hasLeadingZero(part) || !containsOnlyDigits(part)) {
        return null;
      }

      try {
        int octet = Integer.parseInt(part);

        if (octet < 0 || octet > 255) {
          return null;
        }

        octets[index] = octet;
      } catch (NumberFormatException e) {
        return null;
      }
    }

    return octets;
  }

  /**
   * Detecta hosts compostos apenas por dígitos e pontos para bloquear valores que parecem IPv4
   * malformado.
   */
  private boolean looksLikeIpv4Literal(String host) {
    return host.matches("[0-9.]+");
  }

  private boolean containsOnlyDigits(String value) {
    for (int index = 0; index < value.length(); index++) {
      if (!Character.isDigit(value.charAt(index))) {
        return false;
      }
    }

    return true;
  }

  private boolean hasLeadingZero(String value) {
    return value.length() > 1 && value.startsWith("0");
  }

  /** Bloqueia faixas IPv6 de loopback, link-local, unspecified, multicast e unique-local. */
  private boolean isBlockedIpv6Literal(String host) {
    Inet6Address address = parseIpv6Literal(host);

    if (address == null) {
      return false;
    }

    byte[] bytes = address.getAddress();

    return isIpv6Unspecified(bytes)
        || isIpv6Loopback(bytes)
        || isIpv6LinkLocal(bytes)
        || isIpv6UniqueLocal(bytes)
        || isIpv6Multicast(bytes);
  }

  /**
   * Faz parsing de IPv6 apenas quando o texto do host contém dois-pontos. Isso mantém {@link
   * InetAddress} fora de hostnames comuns e evita resolução DNS no caminho normal de nomes de
   * domínio.
   */
  private Inet6Address parseIpv6Literal(String host) {
    if (!host.contains(":")) {
      return null;
    }

    try {
      InetAddress address = InetAddress.getByName(host);

      if (address instanceof Inet6Address inet6Address) {
        return inet6Address;
      }
    } catch (Exception e) {
      return null;
    }

    return null;
  }

  private boolean isIpv6Unspecified(byte[] bytes) {
    for (byte value : bytes) {
      if (value != 0) {
        return false;
      }
    }

    return true;
  }

  private boolean isIpv6Loopback(byte[] bytes) {
    for (int index = 0; index < bytes.length - 1; index++) {
      if (bytes[index] != 0) {
        return false;
      }
    }

    return bytes[bytes.length - 1] == 1;
  }

  private boolean isIpv6LinkLocal(byte[] bytes) {
    return Byte.toUnsignedInt(bytes[0]) == 0xfe && (Byte.toUnsignedInt(bytes[1]) & 0xc0) == 0x80;
  }

  private boolean isIpv6UniqueLocal(byte[] bytes) {
    return (Byte.toUnsignedInt(bytes[0]) & 0xfe) == 0xfc;
  }

  private boolean isIpv6Multicast(byte[] bytes) {
    return Byte.toUnsignedInt(bytes[0]) == 0xff;
  }
}
