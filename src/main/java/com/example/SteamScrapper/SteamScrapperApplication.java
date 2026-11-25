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

@SpringBootApplication
public class SteamScrapperApplication {

    record JogoExtraido(String chave, String nome) {}

    public static void main(String[] args) {
        SpringApplication.run(SteamScrapperApplication.class, args);
        List <String> listaLinksJogosITAD = new ArrayList<>();
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
                List<String> encontrados = new ArrayList<>();

                System.out.println("Nome jogo: "+ j.nome());

                String slugDoJogo = transformarNomeEmLink(j.nome());
                if (!slugDoJogo.isBlank()) {
                    String urlIsThereAnyDeal = "https://isthereanydeal.com/game/" + slugDoJogo + "/info/";
                    System.out.println("link do jogo na ITAD: " + urlIsThereAnyDeal);
                    listaLinksJogosITAD.add(urlIsThereAnyDeal);
                    System.out.println(urlIsThereAnyDeal);
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

            destino.putIfAbsent(chave, new JogoExtraido(chave, nome));
        }
    }
}