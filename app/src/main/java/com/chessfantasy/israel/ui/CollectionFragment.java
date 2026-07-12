package com.chessfantasy.israel.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.chessfantasy.israel.R;
import com.chessfantasy.israel.data.GameRepository;
import com.chessfantasy.israel.model.Card;

import java.util.List;

public class CollectionFragment extends Fragment implements Refreshable {

    private RecyclerView recycler;
    private TextView empty;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_collection, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        recycler = view.findViewById(R.id.collection_recycler);
        empty = view.findViewById(R.id.collection_empty);
        recycler.setLayoutManager(new GridLayoutManager(requireContext(), 2));
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshData();
    }

    @Override
    public void refreshData() {
        if (recycler == null || !isAdded()) return;
        List<Card> cards = GameRepository.get().myCards();
        empty.setVisibility(cards.isEmpty() ? View.VISIBLE : View.GONE);
        recycler.setAdapter(new CardAdapter(cards, false, card -> {
            Intent intent = new Intent(requireContext(), CardDetailActivity.class);
            intent.putExtra(CardDetailActivity.EXTRA_CARD_ID, card.id);
            startActivity(intent);
        }));
    }
}
