package com.chessfantasy.israel.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.chessfantasy.israel.R;
import com.chessfantasy.israel.data.GameRepository;
import com.chessfantasy.israel.model.Auction;
import com.chessfantasy.israel.model.Card;
import com.chessfantasy.israel.model.SaleListing;
import com.chessfantasy.israel.model.TradeOffer;
import com.chessfantasy.israel.util.Format;
import com.google.android.material.button.MaterialButtonToggleGroup;

import java.util.List;

public class MarketFragment extends Fragment implements Refreshable {

    private RecyclerView recycler;
    private TextView empty;
    private MaterialButtonToggleGroup tabs;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_market, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        recycler = view.findViewById(R.id.market_recycler);
        empty = view.findViewById(R.id.market_empty);
        tabs = view.findViewById(R.id.market_tabs);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        tabs.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) refreshData();
        });
        tabs.check(R.id.tab_auctions);
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshData();
    }

    @Override
    public void refreshData() {
        if (recycler == null || !isAdded()) return;
        GameRepository repo = GameRepository.get();
        int checked = tabs.getCheckedButtonId();

        if (checked == R.id.tab_sales) {
            List<SaleListing> sales = repo.activeSales();
            empty.setVisibility(sales.isEmpty() ? View.VISIBLE : View.GONE);
            recycler.setAdapter(new SaleAdapter(sales, new SaleAdapter.Callbacks() {
                @Override
                public void onBuy(SaleListing sale) {
                    confirmBuy(sale);
                }

                @Override
                public void onOffer(SaleListing sale) {
                    openTradeOffer(sale.cardId, sale.sellerId);
                }

                @Override
                public void onCancel(SaleListing sale) {
                    toastError(repo.cancelSale(sale.id));
                    refreshData();
                }
            }));
        } else if (checked == R.id.tab_offers) {
            List<TradeOffer> offers = repo.offersInvolvingMe();
            empty.setVisibility(offers.isEmpty() ? View.VISIBLE : View.GONE);
            recycler.setAdapter(new OfferAdapter(offers, new OfferAdapter.Callbacks() {
                @Override
                public void onAccept(TradeOffer offer) {
                    String error = repo.acceptOffer(offer.id);
                    Toast.makeText(requireContext(),
                            error == null ? "Trade completed!" : error, Toast.LENGTH_SHORT).show();
                    refreshData();
                }

                @Override
                public void onReject(TradeOffer offer) {
                    toastError(repo.rejectOffer(offer.id));
                    refreshData();
                }
            }));
        } else {
            List<Auction> auctions = repo.activeAuctions();
            empty.setVisibility(auctions.isEmpty() ? View.VISIBLE : View.GONE);
            recycler.setAdapter(new AuctionAdapter(auctions, new AuctionAdapter.Callbacks() {
                @Override
                public void onBid(Auction auction) {
                    promptBid(auction);
                }

                @Override
                public void onOffer(Auction auction) {
                    openTradeOffer(auction.cardId, auction.sellerId);
                }

                @Override
                public void onCancel(Auction auction) {
                    toastError(repo.cancelAuction(auction.id));
                    refreshData();
                }
            }));
        }
    }

    private void promptBid(Auction auction) {
        GameRepository repo = GameRepository.get();
        NumberPrompt.show(requireContext(),
                "Place bid (min " + Format.pawns(auction.nextMinBid()) + " Pawns)",
                auction.nextMinBid(),
                amount -> {
                    String error = repo.placeBid(auction.id, amount);
                    Toast.makeText(requireContext(),
                            error == null ? "Bid placed!" : error, Toast.LENGTH_SHORT).show();
                    refreshData();
                });
    }

    private void confirmBuy(SaleListing sale) {
        GameRepository repo = GameRepository.get();
        Card card = repo.getCard(sale.cardId);
        if (card == null) return;
        String label = card.rarity.displayName + " "
                + repo.playerOrUnknown(card.playerId).titledName() + " " + card.serialLabel();
        new AlertDialog.Builder(requireContext())
                .setTitle("Buy now")
                .setMessage("Buy " + label + " for " + Format.pawns(sale.price) + " Pawns?")
                .setPositiveButton(R.string.buy, (d, w) -> {
                    String error = repo.buyNow(sale.id);
                    Toast.makeText(requireContext(),
                            error == null ? "Card is yours!" : error, Toast.LENGTH_SHORT).show();
                    refreshData();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openTradeOffer(String cardId, String ownerId) {
        if (GameRepository.USER_ID.equals(ownerId)) return;
        Intent intent = new Intent(requireContext(), TradeOfferActivity.class);
        intent.putExtra(TradeOfferActivity.EXTRA_TO_ID, ownerId);
        intent.putExtra(TradeOfferActivity.EXTRA_REQUESTED_CARD_ID, cardId);
        startActivity(intent);
    }

    private void toastError(@Nullable String error) {
        if (error != null) {
            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show();
        }
    }
}
