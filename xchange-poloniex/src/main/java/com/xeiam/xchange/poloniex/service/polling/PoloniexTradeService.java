package com.xeiam.xchange.poloniex.service.polling;

/**
 * @author Zach Holmes
 */

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.xeiam.xchange.ExchangeException;
import com.xeiam.xchange.ExchangeSpecification;
import com.xeiam.xchange.NotAvailableFromExchangeException;
import com.xeiam.xchange.NotYetImplementedForExchangeException;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.Order.OrderType;
import com.xeiam.xchange.dto.marketdata.TradeServiceHelper;
import com.xeiam.xchange.dto.marketdata.Trades.TradeSortType;
import com.xeiam.xchange.dto.trade.LimitOrder;
import com.xeiam.xchange.dto.trade.MarketOrder;
import com.xeiam.xchange.dto.trade.OpenOrders;
import com.xeiam.xchange.dto.trade.UserTrade;
import com.xeiam.xchange.dto.trade.UserTrades;
import com.xeiam.xchange.poloniex.PoloniexAdapters;
import com.xeiam.xchange.poloniex.PoloniexUtils;
import com.xeiam.xchange.poloniex.dto.trade.PoloniexOpenOrder;
import com.xeiam.xchange.poloniex.dto.trade.PoloniexUserTrade;
import com.xeiam.xchange.service.polling.PollingTradeService;
import com.xeiam.xchange.service.polling.trade.TradeHistoryParamCurrencyPair;
import com.xeiam.xchange.service.polling.trade.TradeHistoryParams;
import com.xeiam.xchange.service.polling.trade.TradeHistoryParamsAll;
import com.xeiam.xchange.service.polling.trade.TradeHistoryParamsTimeSpan;
import com.xeiam.xchange.utils.DateUtils;

public class PoloniexTradeService extends PoloniexTradeServiceRaw implements PollingTradeService {

  public PoloniexTradeService(ExchangeSpecification exchangeSpecification) {

    super(exchangeSpecification);
  }

  @Override
  public OpenOrders getOpenOrders() throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {

    HashMap<String, PoloniexOpenOrder[]> poloniexOpenOrders = returnOpenOrders();
    return PoloniexAdapters.adaptPoloniexOpenOrders(poloniexOpenOrders);
  }

  @Override
  public String placeMarketOrder(MarketOrder marketOrder) throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {

    throw new NotAvailableFromExchangeException();
  }

  @Override
  public String placeLimitOrder(LimitOrder limitOrder) throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {

    if (limitOrder.getType() == OrderType.BID) {
      return buy(limitOrder).getOrderNumber().toString();
    }
    else {
      return sell(limitOrder).getOrderNumber().toString();
    }
  }

  @Override
  public boolean cancelOrder(String orderId) throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {

    return cancel(orderId);
  }

  @Override
  public UserTrades getTradeHistory(Object... arguments) throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {

    CurrencyPair currencyPair = null;
    Long startTime = null;
    Long endTime = null;

    if (arguments != null) {
      switch (arguments.length) {
      case 3:
        if (arguments[2] != null && arguments[2] instanceof Long) {
          endTime = (Long) arguments[2];
        }
      case 2:
        if (arguments[1] != null && arguments[1] instanceof Long) {
          startTime = (Long) arguments[1];
        }
      case 1:
        if (arguments[0] != null && arguments[0] instanceof CurrencyPair) {
          currencyPair = (CurrencyPair) arguments[0];
        }
      }
    }
    return getTradeHistory(currencyPair, startTime, endTime);
  }

  /**
   * @param params
   *          Can optionally implement {@link TradeHistoryParamCurrencyPair} and {@link TradeHistoryParamsTimeSpan}. All other TradeHistoryParams types will be ignored.
   */

  @Override
  public UserTrades getTradeHistory(TradeHistoryParams params) throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {

    CurrencyPair currencyPair = null;
    Date startTime = null;
    Date endTime = null;

    if (params instanceof TradeHistoryParamCurrencyPair) {
      currencyPair = ((TradeHistoryParamCurrencyPair) params).getCurrencyPair();
    }
    if (params instanceof TradeHistoryParamsTimeSpan) {
      startTime = ((TradeHistoryParamsTimeSpan) params).getStartTime();
      endTime = ((TradeHistoryParamsTimeSpan) params).getEndTime();
    }
    return getTradeHistory(currencyPair, DateUtils.toUnixTimeNullSafe(startTime), DateUtils.toUnixTimeNullSafe(endTime));
  }

  private UserTrades getTradeHistory(CurrencyPair currencyPair, final Long startTime, final Long endTime) throws IOException {

    List<UserTrade> trades = new ArrayList<UserTrade>();
    if (currencyPair == null) {
      HashMap<String, PoloniexUserTrade[]> poloniexUserTrades = returnTradeHistory(startTime, endTime);
      for (Map.Entry<String, PoloniexUserTrade[]> mapEntry : poloniexUserTrades.entrySet()) {
        currencyPair = PoloniexUtils.toCurrencyPair(mapEntry.getKey());
        for (PoloniexUserTrade poloniexUserTrade : mapEntry.getValue()) {
          trades.add(PoloniexAdapters.adaptPoloniexUserTrade(poloniexUserTrade, currencyPair));
        }
      }
    }
    else {
      PoloniexUserTrade[] poloniexUserTrades = returnTradeHistory(currencyPair, startTime, endTime);
      for (PoloniexUserTrade poloniexUserTrade : poloniexUserTrades) {
        trades.add(PoloniexAdapters.adaptPoloniexUserTrade(poloniexUserTrade, currencyPair));
      }
    }

    return new UserTrades(trades, TradeSortType.SortByTimestamp);
  }

  /**
   * Create {@link TradeHistoryParams} that supports {@link TradeHistoryParamsTimeSpan} and {@link TradeHistoryParamCurrencyPair}.
   */

  @Override
  public TradeHistoryParams createTradeHistoryParams() {

    return new PoloniexTradeHistoryParams();
  }

  /**
   * Fetch the {@link com.xeiam.xchange.dto.marketdata.TradeServiceHelper} from the exchange.
   *
   * @return Map of currency pairs to their corresponding metadata.
   * @see com.xeiam.xchange.dto.marketdata.TradeServiceHelper
   */
  @Override public Map<CurrencyPair, ? extends TradeServiceHelper> getTradeServiceHelperMap() throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {
    throw new NotAvailableFromExchangeException();
  }

  public static class PoloniexTradeHistoryParams implements TradeHistoryParamCurrencyPair, TradeHistoryParamsTimeSpan {

    private final TradeHistoryParamsAll all = new TradeHistoryParamsAll();

    @Override
    public void setCurrencyPair(CurrencyPair value) {

      all.setCurrencyPair(value);
    }

    @Override
    public CurrencyPair getCurrencyPair() {

      return all.getCurrencyPair();
    }

    @Override
    public void setStartTime(Date value) {

      all.setStartTime(value);
    }

    @Override
    public Date getStartTime() {

      return all.getStartTime();
    }

    @Override
    public void setEndTime(Date value) {

      all.setEndTime(value);
    }

    @Override
    public Date getEndTime() {

      return all.getEndTime();
    }
  }

}
