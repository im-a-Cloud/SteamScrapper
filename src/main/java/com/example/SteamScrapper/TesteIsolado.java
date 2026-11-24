package com.example.SteamScrapper;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.BoundingBox;
import com.microsoft.playwright.options.WaitUntilState;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TesteIsolado {

    private static final Pattern PADRAO_PRECO = Pattern.compile("R\\$\\s*[\\d.,]+");
    private static final Pattern PADRAO_DESCONTO = Pattern.compile("-\\d+%");
    private static final Pattern PADRAO_PORCENTAGEM = Pattern.compile("-?\\d+\\s*%");
    private static final Pattern PADRAO_DIGITOS = Pattern.compile("\\d+");

    public record AllTimeLowInfo(String valor, String porcentagem) {}

    public record OfertaExtraida(
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
        // URL de teste (ex.: rota /history/ para inspecionar o gráfico)
        String urlAlvo = "https://isthereanydeal.com/game/yakuza-5-remastered/info/";

        // Mude para false se quiser acompanhar visualmente o Chromium
        boolean headless = true;

        // Quantidade de posições X varridas ao longo do gráfico (ajuste conforme velocidade/precisão)
        int passosVarredura = 120;

        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(
                     new BrowserType.LaunchOptions().setHeadless(headless)
             )) {

            Page page = browser.newPage();

            // 1. Navega e aguarda rede estabilizar
            page.navigate(urlAlvo, new Page.NavigateOptions().setWaitUntil(WaitUntilState.NETWORKIDLE));

            // 2. Aguarda o container ou linha de rastreamento do gráfico aparecer
            try {
                page.waitForSelector(".highcharts-container, .highcharts-tracker-line",
                        new Page.WaitForSelectorOptions().setTimeout(10000));
            } catch (Exception ignored) {
                // Página pode não conter gráfico renderizado
            }

            page.waitForTimeout(1500);

            // 3. Varre o gráfico com o mouse para acionar e ler tooltips
            BigDecimal menorValorGrafico = varrerGraficoPorMouse(page, passosVarredura);
            System.out.println("Menor valor encontrado na varredura do gráfico: " + menorValorGrafico);

            // 4. Parseia HTML atual com Jsoup (All time low + ofertas da Steam)
            String html = page.content();
            Document doc = Jsoup.parse(html);

            System.out.println("URL testada: " + urlAlvo);
            AllTimeLowInfo atl = extrairAllTimeLow(doc);
            System.out.println("All Time Low: " + atl);

            Elements linhas = doc.select("a.row");
            System.out.println("Total de linhas .row encontradas: " + linhas.size());
            System.out.println("==========================================");

            boolean encontrouSteam = false;
            for (Element linha : linhas) {
                OfertaExtraida oferta = extrairOfertaSteam(linha);
                if (oferta == null) {
                    continue;
                }
                encontrouSteam = true;

                System.out.println("URL Oferta       : " + oferta.urlOferta());
                System.out.println("Loja             : " + oferta.loja());
                System.out.println("Promoção         : " + oferta.tipoPromocao());
                System.out.println("Menor preço (low): " + oferta.menorPrecoHistorico());
                System.out.println("Diferença low    : " + oferta.diferencaMenorPreco());
                System.out.println("Desconto         : " + oferta.desconto());
                System.out.println("Tempo restante   : " + oferta.tempoRestante());
                System.out.println("Preço atual      : " + oferta.precoAtual());
                System.out.println("Preço antigo     : " + oferta.precoAntigo());
                System.out.println("------------------------------------------");
            }

            if (!encontrouSteam) {
                System.out.println("Nenhuma oferta da Steam encontrada nessa URL.");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static BigDecimal varrerGraficoPorMouse(Page page, int passos) {
        if (page == null || passos <= 0) {
            return null;
        }

        Locator alvoArea = page.locator(".highcharts-plot-background").first();
        if (alvoArea.count() == 0) {
            alvoArea = page.locator(".highcharts-tracker-line").first();
        }
        if (alvoArea.count() == 0) {
            alvoArea = page.locator(".highcharts-container").first();
        }
        if (alvoArea.count() == 0) {
            return null;
        }

        try {
            alvoArea.scrollIntoViewIfNeeded(new Locator.ScrollIntoViewIfNeededOptions().setTimeout(3000));
            page.waitForTimeout(200);
        } catch (Exception ignored) {
        }

        BoundingBox box = alvoArea.boundingBox();
        if (box == null || box.width <= 0 || box.height <= 0) {
            return null;
        }

        BigDecimal menorValor = null;
        // Usa o meio vertical da área de plotagem como linha base de varredura
        double y = box.y + (box.height / 2.0);

        for (int i = 0; i <= passos; i++) {
            double x = box.x + (box.width * ((double) i / passos));
            page.mouse().move(x, y);
            page.waitForTimeout(35);

            String tooltip = page.locator(".highcharts-tooltip").allInnerTexts().stream()
                    .map(TesteIsolado::normalizarEspacos)
                    .filter(t -> !t.isBlank())
                    .findFirst()
                    .orElse("");

            if (tooltip.isBlank()) {
                continue;
            }

            Matcher matcher = PADRAO_PRECO.matcher(tooltip);
            while (matcher.find()) {
                BigDecimal valor = parsePrecoBrl(matcher.group());
                if (valor != null && (menorValor == null || valor.compareTo(menorValor) < 0)) {
                    menorValor = valor;
                }
            }
        }

        return menorValor;
    }

    public static OfertaExtraida extrairOfertaSteam(Element row) {
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

    private static String normalizarEspacos(String texto) {
        if (texto == null) {
            return "";
        }
        return texto.replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    public static AllTimeLowInfo extrairAllTimeLow(Document doc) {
        if (doc == null) {
            return new AllTimeLowInfo("", "");
        }

        Element labelAtl = doc.selectFirst(":matchesOwn((?i)^\\s*all[-\\s]*time\\s*low\\s*$)");
        if (labelAtl == null) {
            labelAtl = doc.selectFirst(":containsOwn(All time low)");
        }

        if (labelAtl == null) {
            return new AllTimeLowInfo("", "");
        }

        Element bloco = labelAtl;
        String textoBloco = "";
        for (int i = 0; i < 3 && bloco != null; i++) {
            textoBloco = normalizarEspacos(bloco.text());
            boolean temPreco = PADRAO_PRECO.matcher(textoBloco).find();
            boolean temPct = PADRAO_PORCENTAGEM.matcher(textoBloco).find();
            if (temPreco && temPct) {
                break;
            }
            bloco = bloco.parent();
        }

        if (bloco != null) {
            textoBloco = normalizarEspacos(bloco.text());
        }

        String valor = "";
        Matcher mPreco = PADRAO_PRECO.matcher(textoBloco);
        if (mPreco.find()) {
            valor = mPreco.group().replaceAll("\\s+", " ");
        }

        String porcentagem = "";
        Matcher mPct = PADRAO_PORCENTAGEM.matcher(textoBloco);
        if (mPct.find()) {
            porcentagem = mPct.group().replaceAll("\\s+", "");
        }

        double dinheiro = dinheiroEmNumero(valor);
        int pctNumero = descontoEmNumero(porcentagem);
        if (dinheiro > 0 && pctNumero > 0 && pctNumero < 100) {
            double decimalDesconto = 1.0 - (pctNumero / 100.0);
            double precoCheioEstimado = dinheiro / decimalDesconto;
            System.out.println("Preço cheio estimado: " + precoCheioEstimado);
        }

        return new AllTimeLowInfo(valor, porcentagem);
    }

    public static double dinheiroEmNumero(String valor) {
        BigDecimal decimal = parsePrecoBrl(valor);
        return decimal != null ? decimal.doubleValue() : 0.0;
    }

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
}