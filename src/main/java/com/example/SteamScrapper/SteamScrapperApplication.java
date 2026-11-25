package com.example.SteamScrapper;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.WaitUntilState;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SpringBootApplication
public class SteamScrapperApplication {

    private static final Pattern PADRAO_PRECO = Pattern.compile("R\\$\\s*[\\d.,]+");
    private static final Pattern PADRAO_DESCONTO = Pattern.compile("-\\d+%");
    private static final Pattern PADRAO_DIGITOS = Pattern.compile("\\d+");

    record JogoExtraido(String chave, String nome, String preco) {}

    record OfertaExtraida(
            String urlOferta,
            String loja,
            String tipoPromocao,
            String menorPrecoHistorico,
            String diferencaMenorPreco,
            String desconto,
            String tempoRestante,
            String precoAtual,
            String precoAntigo
    ) {}

    public static void main(String[] args) {
        SpringApplication.run(SteamScrapperApplication.class, args);

        String urlWlSteam = "https://store.steampowered.com/wishlist/profiles/76561199092903131/?sort=discount";
        Map<String, JogoExtraido> jogos = new LinkedHashMap<>();

        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(
                     new BrowserType.LaunchOptions().setHeadless(true)
             )) {

            Page page = browser.newPage();

            page.onResponse(response -> {
                String contentType = response.headers().getOrDefault("content-type", "");
                if (contentType.contains("application/json") && response.status() == 200) {
                    System.out.println("[JSON] " + response.url());
                }
            });

            page.navigate(urlWlSteam, new Page.NavigateOptions().setWaitUntil(WaitUntilState.NETWORKIDLE));
            page.waitForSelector(
                    "[data-rfd-draggable-id^=WishlistItem-], [data-rfd-drag-handle-draggable-id^=WishlistItem-], .PE-3oq-yIvg-",
                    new Page.WaitForSelectorOptions().setTimeout(15000)
            );

            int semAvanco = 0;
            for (int i = 0; i < 40; i++) {
                int antes = jogos.size();
                coletarDoDom(page.content(), jogos);

                if (jogos.size() > antes) {
                    semAvanco = 0;
                    System.out.println("Passo " + i + " | Itens acumulados: " + jogos.size());
                } else {
                    semAvanco++;
                }

                if (semAvanco >= 5) {
                    break;
                }

                page.evaluate("""
                        () => {
                            window.scrollBy(0, 1200);
                            document.querySelectorAll('*').forEach(el => {
                                if (el.scrollHeight > el.clientHeight + 50) {
                                    el.scrollTop += 1200;
                                }
                            });
                        }
                        """);
                page.waitForTimeout(1000);
            }

            for (var j : jogos.values()) {
                String textoPreco = j.preco() != null ? j.preco() : "";
                List<String> encontrados = new ArrayList<>();

                Matcher matcher = PADRAO_PRECO.matcher(textoPreco);
                while (matcher.find()) {
                    encontrados.add(matcher.group().replaceAll("\\s+", ""));
                }

                String precoCheio = !encontrados.isEmpty() ? encontrados.get(0) : "Não encontrado";
                String precoDesconto = encontrados.size() >= 2 ? encontrados.get(1) : "Não encontrado";

                System.out.println(j.nome() + " | Cheio: " + precoCheio + " | Desconto: " + precoDesconto);

                String slugDoJogo = transformarNomeEmLink(j.nome());
                if (!slugDoJogo.isBlank()) {
                    String urlIsThereAnyDeal = "https://isthereanydeal.com/game/" + slugDoJogo + "/info/";
                    System.out.println("Consultando: " + urlIsThereAnyDeal);

                    String htmlOferta = scrapDaPaginaDoJogo(browser, urlIsThereAnyDeal);
                    if (!htmlOferta.isBlank()) {
                        coletarIsThereAnyDeal(htmlOferta);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String normalizarEspacos(String texto) {
        if (texto == null) {
            return "";
        }
        return texto.replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    public static String transformarNomeEmLink(String nomeJogo) {
        if (nomeJogo == null || nomeJogo.isBlank()) {
            return "";
        }

        String semAcento = Normalizer.normalize(nomeJogo, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");

        return semAcento
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
    }

    public static String scrapDaPaginaDoJogo(Browser browser, String linkJogo) {
        try {
            Page page = browser.newPage();
            try {
                page.navigate(linkJogo, new Page.NavigateOptions().setWaitUntil(WaitUntilState.NETWORKIDLE));
                page.waitForTimeout(1500);
                return page.content();
            } finally {
                page.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    private static void coletarDoDom(String html, Map<String, JogoExtraido> destino) {
        Document doc = Jsoup.parse(html);
        Elements itens = doc.select("[data-rfd-draggable-id^=WishlistItem-], .PE-3oq-yIvg-");

        for (Element item : itens) {
            String dragId = item.attr("data-rfd-draggable-id");
            if (dragId.isBlank()) {
                Element handle = item.selectFirst("[data-rfd-drag-handle-draggable-id]");
                if (handle != null) {
                    dragId = handle.attr("data-rfd-drag-handle-draggable-id");
                }
            }

            Element linkNome = item.select("a[href*=/app/]")
                    .stream()
                    .filter(el -> !el.text().isBlank())
                    .findFirst()
                    .orElse(null);

            String nome = (linkNome != null) ? linkNome.text().trim() : "Nome não encontrado";
            String chave = !dragId.isBlank() ? dragId : (linkNome != null ? linkNome.attr("href") : nome);

            Element precoEl = item.selectFirst("[aria-label*=R$]");
            String preco;
            if (precoEl != null) {
                preco = normalizarEspacos(precoEl.attr("aria-label"));
            } else {
                Matcher m = PADRAO_PRECO.matcher(normalizarEspacos(item.text()));
                List<String> achados = new ArrayList<>();
                while (m.find()) {
                    achados.add(m.group().replaceAll("\\s+", ""));
                }
                preco = !achados.isEmpty() ? String.join(" / ", achados) : "Preço não encontrado";
            }

            destino.putIfAbsent(chave, new JogoExtraido(chave, nome, preco));
        }
    }

    public static double parsePrecoDouble(String textoPreco) {
        if (textoPreco == null || textoPreco.isBlank()) {
            return 0.0;
        }
        Matcher matcher = PADRAO_PRECO.matcher(textoPreco.replace('\u00A0', ' '));
        if (!matcher.find()) {
            return 0.0;
        }
        String limpo = matcher.group()
                .replace("R$", "")
                .replaceAll("\\s+", "")
                .replace(".", "")
                .replace(",", ".")
                .trim();

        return limpo.matches("\\d+(\\.\\d+)?") ? Double.parseDouble(limpo) : 0.0;
    }

    public static double parseDescontoDouble(String textoDesconto) {
        if (textoDesconto == null || textoDesconto.isBlank()) {
            return 0.0;
        }
        Matcher matcher = PADRAO_DIGITOS.matcher(textoDesconto);
        return matcher.find() ? Double.parseDouble(matcher.group()) : 0.0;
    }

    public static OfertaExtraida extrairOferta(Element row) {
        if (row == null) {
            return null;
        }

        Element spanLoja = row.selectFirst(".cell--shop span");
        String loja = spanLoja != null ? normalizarEspacos(spanLoja.ownText()) : "";

        if (!"Steam".equalsIgnoreCase(loja)) {
            return null;
        }

        String urlOferta = normalizarEspacos(row.attr("href"));

        Element promoEl = row.selectFirst(".cell--shop .promo");
        String tipoPromocao = promoEl != null ? normalizarEspacos(promoEl.text()) : "";

        String menorPrecoHistorico = "";
        String diferencaMenorPreco = "";
        Element cellLow = row.selectFirst(".cell--low");
        if (cellLow != null) {
            String textoLow = normalizarEspacos(cellLow.text());
            Matcher mLow = PADRAO_PRECO.matcher(textoLow);
            List<String> precosLow = new ArrayList<>();
            while (mLow.find()) {
                precosLow.add(mLow.group().replaceAll("\\s+", " "));
            }
            if (!precosLow.isEmpty()) {
                menorPrecoHistorico = precosLow.get(0);
            }
            if (precosLow.size() >= 2) {
                diferencaMenorPreco = "lower by " + precosLow.get(1);
            } else if (textoLow.toLowerCase(Locale.ROOT).contains("same")) {
                diferencaMenorPreco = "same";
            }
        }

        String desconto = "";
        Element cutCell = row.selectFirst(".cell--cut");
        if (cutCell != null) {
            Matcher mCut = PADRAO_DESCONTO.matcher(normalizarEspacos(cutCell.text()));
            if (mCut.find()) {
                desconto = mCut.group();
            }
        }

        Element expiryEl = row.selectFirst(".cell--cut .expiry");
        String tempoRestante = expiryEl != null ? normalizarEspacos(expiryEl.text()) : "";

        String precoAtual = "";
        String precoAntigo = "";
        Element cellPrice = row.selectFirst(".cell--price");
        if (cellPrice != null) {
            Element oldEl = cellPrice.selectFirst(".old");
            if (oldEl != null) {
                Matcher mOld = PADRAO_PRECO.matcher(normalizarEspacos(oldEl.text()));
                if (mOld.find()) {
                    precoAntigo = mOld.group().replaceAll("\\s+", " ");
                }
            }

            Element copiaPreco = cellPrice.clone();
            Element oldNaCopia = copiaPreco.selectFirst(".old");
            if (oldNaCopia != null) {
                oldNaCopia.remove();
            }
            Matcher mAtual = PADRAO_PRECO.matcher(normalizarEspacos(copiaPreco.text()));
            if (mAtual.find()) {
                precoAtual = mAtual.group().replaceAll("\\s+", " ");
            }

            double precoAtualDouble = parsePrecoDouble(precoAtual);
            double descontoDouble = parseDescontoDouble(desconto);
            double precoAntigoDouble = parsePrecoDouble(precoAntigo);

            if (precoAtualDouble <= 0) {
                System.out.println("Aviso: preço atual zerado ou inválido para " + urlOferta);
            }
            if (precoAntigoDouble == 0 && precoAtualDouble > 0 && descontoDouble > 0 && descontoDouble < 100) {
                precoAntigoDouble = precoAtualDouble / ((100.0 - descontoDouble) / 100.0);
                precoAntigo = String.format(Locale.forLanguageTag("pt-BR"), "R$ %.2f", precoAntigoDouble);
            }
        }

        return new OfertaExtraida(
                urlOferta,
                loja,
                tipoPromocao,
                menorPrecoHistorico,
                diferencaMenorPreco,
                desconto,
                tempoRestante,
                precoAtual,
                precoAntigo
        );
    }

    public static void coletarIsThereAnyDeal(String html) {
        Document doc = Jsoup.parse(html);
        Elements linhas = doc.select("a.row");

        boolean encontrouSteam = false;
        for (Element linha : linhas) {
            OfertaExtraida oferta = extrairOferta(linha);
            if (oferta == null) {
                continue;
            }
            encontrouSteam = true;

            System.out.println("URL: " + oferta.urlOferta());
            System.out.println("Loja: " + oferta.loja());
            System.out.println("Menor preço (store low): " + oferta.menorPrecoHistorico());
            System.out.println("Desconto: " + oferta.desconto());
            System.out.println("Preço atual: " + oferta.precoAtual());
            System.out.println("Preço antigo: " + oferta.precoAntigo());
            System.out.println("-----------------------------------");
        }

        if (!encontrouSteam) {
            System.out.println("Nenhuma oferta da Steam encontrada na página.");
        }
    }
}