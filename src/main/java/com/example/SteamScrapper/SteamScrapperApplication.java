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

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private static final Pattern PADRAO_PORCENTAGEM = Pattern.compile("-?\\d+\\s*%");
    private static final Pattern PADRAO_DIGITOS = Pattern.compile("\\d+");
    // Limiar para suspeitar que o menor histórico é de preço antiga (reajuste forte)
    private static final int LIMIAR_DESCONTO_ANOMALO_PCT = 85;

    public record OfertaSteamBruta(
            String urlOferta,
            String loja,
            String tipoPromocao,
            String menorPrecoHistorico,
            String descontoHistoricoExplicito,
            String diferencaMenorPreco,
            String descontoAtual,
            String tempoRestante,
            String precoAtual,
            String precoCheioExibido
    ) {}

    public record AnalisePrecoSteam(
            String urlOferta,
            BigDecimal precoAtual,
            BigDecimal precoCheioAtual,
            int descontoAtualPct,
            BigDecimal menorPrecoHistorico,
            int descontoHistoricoPct,
            boolean descontoHistoricoEstimado,
            boolean houveReajusteForte,
            String observacao
    ) {}

    record JogoExtraido(String chave, String nome) {}

    public static void main(String[] args) {
        SpringApplication.run(SteamScrapperApplication.class, args);
        /*
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

                String slugDoJogo = transformarNomeEmLink(j.nome());
                if (!slugDoJogo.isBlank()) {
                    String urlIsThereAnyDeal = "https://isthereanydeal.com/game/" + slugDoJogo + "/info/";
                    listaLinksJogosITAD.add(urlIsThereAnyDeal);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        //apenas lê a URL sem abrir o navegador
        boolean headless = true;
        //cria o playwright que vai abrir e ler a página no modo headless
        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(
                     new BrowserType.LaunchOptions().setHeadless(headless)
             )) {

            Page page = browser.newPage();

            for (String urlAlvo : listaLinksJogosITAD) {
                System.out.println("==========================================");
                System.out.println("URL testada: " + urlAlvo);

                page.navigate(urlAlvo, new Page.NavigateOptions().setWaitUntil(WaitUntilState.NETWORKIDLE));
                page.waitForTimeout(1000);
                //pegar o gráfico de preços do jogo
                Document doc = Jsoup.parse(page.content());
                Elements linhas = doc.select("a.row");
                //leva em conta somente ofertas na steam
                boolean encontrouSteam = false;
                for (Element linha : linhas) {
                    OfertaSteamBruta bruta = extrairOfertaSteam(linha);
                    if (bruta == null) {
                        continue;
                    }
                    encontrouSteam = true;

                    AnalisePrecoSteam analise = analisarPrecosSteam(bruta);
                    imprimirAnalise(bruta, analise);
                }

                if (!encontrouSteam) {
                    System.out.println("Nenhuma oferta da Steam encontrada nessa URL.");
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static AnalisePrecoSteam analisarPrecosSteam(OfertaSteamBruta bruta) {
        BigDecimal precoAtual = parsePrecoBrl(bruta.precoAtual());
        BigDecimal precoCheio = parsePrecoBrl(bruta.precoCheioExibido());
        BigDecimal menorHistorico = parsePrecoBrl(bruta.menorPrecoHistorico());

        int descontoAtualPct = descontoEmNumero(bruta.descontoAtual());
        int descontoHistoricoExplicitoPct = descontoEmNumero(bruta.descontoHistoricoExplicito());

        // Preço Cheio Atual Real
        if (precoCheio == null && precoAtual != null) {
            if (descontoAtualPct > 0 && descontoAtualPct < 100) {
                BigDecimal fator = BigDecimal.valueOf(100 - descontoAtualPct)
                        .divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
                precoCheio = precoAtual.divide(fator, 2, RoundingMode.HALF_UP);
            } else {
                // Sem desconto ativo e sem .old: o preço atual é o próprio preço cheio atual
                precoCheio = precoAtual;
            }
        }

        // Determina o Desconto Histórico da Steam e detecta reajustes bruscos de preço base
        int descontoHistoricoFinal = descontoHistoricoExplicitoPct;
        boolean estimado = false;
        boolean houveReajusteForte = false;
        String observacao = "Histórico compatível com o preço base atual.";

        int descontoEquivalenteVsPrecoNovo = 0;
        if (menorHistorico != null && precoCheio != null && precoCheio.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal proporcao = menorHistorico.divide(precoCheio, 4, RoundingMode.HALF_UP);
            descontoEquivalenteVsPrecoNovo = BigDecimal.ONE.subtract(proporcao)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(0, RoundingMode.HALF_UP)
                    .intValue();
        }

        if (descontoHistoricoFinal == 0 && descontoEquivalenteVsPrecoNovo > 0) {
            descontoHistoricoFinal = descontoEquivalenteVsPrecoNovo;
            estimado = true;
        }

        // Se o menor preço histórico equivale a >85% off sobre o preço cheio atual
        // ou diverge muito do desconto histórico explícito, houve aumento de preço base
        if (descontoEquivalenteVsPrecoNovo >= LIMIAR_DESCONTO_ANOMALO_PCT
                || (descontoHistoricoExplicitoPct > 0
                && Math.abs(descontoEquivalenteVsPrecoNovo - descontoHistoricoExplicitoPct) >= 15)) {
            houveReajusteForte = true;
            observacao = "ALERTA DE REAJUSTE: O menor histórico (R$ " + menorHistorico
                    + ") ocorreu sob um preço base antigo. Sobre o preço cheio atual (R$ "
                    + precoCheio + "), equivaleria a -" + descontoEquivalenteVsPrecoNovo + "%.";
        }

        return new AnalisePrecoSteam(
                bruta.urlOferta(),
                precoAtual,
                precoCheio,
                descontoAtualPct,
                menorHistorico,
                descontoHistoricoFinal,
                estimado,
                houveReajusteForte,
                observacao
        );
    }

    private static void imprimirAnalise(OfertaSteamBruta bruta, AnalisePrecoSteam analise) {
        System.out.println("URL Oferta                     : " + analise.urlOferta());
        System.out.println("Preço atual (Steam)            : R$ " + analise.precoAtual());
        System.out.println("Preço cheio atual (Steam)      : R$ " + analise.precoCheioAtual());
        System.out.println("Desconto atual (Steam)         : -" + analise.descontoAtualPct() + "%");
        System.out.println("Menor preço histórico (Steam)  : R$ " + analise.menorPrecoHistorico());
        System.out.println("Desconto máximo histórico      : -" + analise.descontoHistoricoPct() + "%"
                + (analise.descontoHistoricoEstimado() ? " (estimado)" : " (explícito no HTML)"));
        System.out.println("Houve reajuste forte de preço? : " + (analise.houveReajusteForte() ? "SIM" : "NÃO"));
        System.out.println("Diagnóstico                    : " + analise.observacao());
        if (analise.descontoHistoricoPct() > 0
                && analise.descontoAtualPct() >= analise.descontoHistoricoPct()
                && !analise.houveReajusteForte()) {
            System.out.println("COMPRA SAPORRA");
        }
        System.out.println("------------------------------------------");
    }

    public static OfertaSteamBruta extrairOfertaSteam(Element row) {
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
        String descontoHistoricoExplicito = "";
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

            Matcher mPctLow = PADRAO_PORCENTAGEM.matcher(textoLow);
            if (mPctLow.find()) {
                descontoHistoricoExplicito = mPctLow.group().replaceAll("\\s+", "");
            }
        }

        String descontoAtual = "";
        Element cutCell = row.selectFirst(".cell--cut");
        if (cutCell != null) {
            Matcher mCut = PADRAO_DESCONTO.matcher(normalizarEspacos(cutCell.text()));
            if (mCut.find()) {
                descontoAtual = mCut.group();
            }
        }

        Element expiryEl = row.selectFirst(".cell--cut .expiry");
        String tempoRestante = expiryEl != null ? normalizarEspacos(expiryEl.text()) : "";

        String precoAtual = "";
        String precoCheioExibido = "";
        Element cellPrice = row.selectFirst(".cell--price");
        if (cellPrice != null) {
            Element oldEl = cellPrice.selectFirst(".old");
            if (oldEl != null) {
                Matcher mOld = PADRAO_PRECO.matcher(normalizarEspacos(oldEl.text()));
                if (mOld.find()) {
                    precoCheioExibido = mOld.group().replaceAll("\\s+", " ");
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
        }

        return new OfertaSteamBruta(
                urlOferta,
                loja,
                tipoPromocao,
                menorPrecoHistorico,
                descontoHistoricoExplicito,
                diferencaMenorPreco,
                descontoAtual,
                tempoRestante,
                precoAtual,
                precoCheioExibido
        );
    }
    //remove espaços do nome do jogo para fazer a pesquisa(vai ser usado mais futuramente)
    private static String normalizarEspacos(String texto) {
        if (texto == null) {
            return "";
        }
        return texto.replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }
    //Converte o preço do jogo em número com ponto decimal
    public static BigDecimal parsePrecoBrl(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }

        String textoLimpo = valor.replace('\u00A0', ' ');
        Matcher matcher = PADRAO_PRECO.matcher(textoLimpo);
        if (!matcher.find()) {
            return null;
        }

        String numeroBruto = matcher.group()
                .replace("R$", "")
                .replaceAll("\\s+", "")
                .replace(".", "")
                .replace(",", ".")
                .trim();

        if (!numeroBruto.matches("\\d+(\\.\\d+)?")) {
            return null;
        }
        return new BigDecimal(numeroBruto);
    }
    //converte o desconto em número
    public static int descontoEmNumero(String valor) {
        if (valor == null || valor.isBlank()) {
            return 0;
        }
        Matcher matcher = PADRAO_DIGITOS.matcher(valor);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group());
        }
        return 0;
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
         */
    }
}