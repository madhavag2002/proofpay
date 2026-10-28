package dev.proofpay.proofpay.web;

import dev.proofpay.proofpay.ai.OrderAnalyzer;
import dev.proofpay.proofpay.config.AppProperties;
import dev.proofpay.proofpay.paypal.InvoiceGateway;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Values every page needs: the seller name and whether PayPal and AI are mocked. */
@ControllerAdvice
class GlobalModelAdvice {

    private final AppProperties props;
    private final InvoiceGateway gateway;
    private final OrderAnalyzer analyzer;

    GlobalModelAdvice(AppProperties props, InvoiceGateway gateway, OrderAnalyzer analyzer) {
        this.props = props;
        this.gateway = gateway;
        this.analyzer = analyzer;
    }

    @ModelAttribute
    void common(Model model) {
        model.addAttribute("sellerName", props.sellerName());
        model.addAttribute("paypalMock", gateway.isMock());
        model.addAttribute("aiMock", analyzer.isMock());
    }
}
