package com.chessfantasy.israel.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.chessfantasy.israel.R;
import com.chessfantasy.israel.data.GameRepository;
import com.chessfantasy.israel.model.PackType;
import com.chessfantasy.israel.model.Rarity;
import com.chessfantasy.israel.util.Format;

public class PacksFragment extends Fragment implements Refreshable {

    private RecyclerView recycler;
    private LinearLayout ownedContainer;
    private TextView ownedTitle;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_packs, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        recycler = view.findViewById(R.id.packs_recycler);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        ownedContainer = view.findViewById(R.id.packs_owned_container);
        ownedTitle = view.findViewById(R.id.packs_owned_title);

        Button ad = view.findViewById(R.id.packs_btn_ad);
        ad.setOnClickListener(v -> {
            if (GameRepository.get().adsRemainingToday() <= 0) {
                Toast.makeText(requireContext(), "No more ads today — come back tomorrow!",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            startActivity(new Intent(requireContext(), AdActivity.class));
        });

        Button forge = view.findViewById(R.id.packs_btn_forge);
        forge.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), EssenceActivity.class)));
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshData();
    }

    @Override
    public void refreshData() {
        if (recycler == null || !isAdded()) return;
        recycler.setAdapter(new PackAdapter());
        buildOwnedPacks();
    }

    /** Populates the "Your packs" section from the pack inventory. */
    private void buildOwnedPacks() {
        GameRepository repo = GameRepository.get();
        ownedContainer.removeAllViews();
        boolean any = false;
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (PackType type : PackType.values()) {
            int count = repo.packCount(type);
            if (count <= 0) continue;
            any = true;
            View row = inflater.inflate(R.layout.item_owned_pack, ownedContainer, false);
            row.setBackgroundResource(CardAdapter.backgroundFor(type.tier));
            int primary = CardAdapter.textColorFor(type.tier);
            int secondary = CardAdapter.secondaryTextColorFor(type.tier);

            TextView name = row.findViewById(R.id.owned_name);
            TextView desc = row.findViewById(R.id.owned_desc);
            TextView countView = row.findViewById(R.id.owned_count);
            ImageView icon = row.findViewById(R.id.owned_icon);
            Button open = row.findViewById(R.id.owned_btn_open);

            name.setText(type.displayName);
            name.setTextColor(primary);
            desc.setText(descriptionFor(type));
            desc.setTextColor(secondary);
            countView.setText("×" + count);
            countView.setTextColor(primary);
            icon.setColorFilter(primary);
            open.setOnClickListener(v -> openOwnedPack(type));

            ownedContainer.addView(row);
        }
        ownedTitle.setVisibility(any ? View.VISIBLE : View.GONE);
    }

    private void openOwnedPack(PackType type) {
        Intent intent = new Intent(requireContext(), PackOpeningActivity.class);
        intent.putExtra(PackOpeningActivity.EXTRA_MODE, PackOpeningActivity.MODE_INVENTORY);
        intent.putExtra(PackOpeningActivity.EXTRA_PACK, type.name());
        startActivity(intent);
    }

    private String descriptionFor(PackType type) {
        switch (type) {
            case LIMITED_PACK:
                return getString(R.string.pack_limited_desc);
            case RARE_PACK:
                return getString(R.string.pack_rare_desc);
            case SUPER_RARE_PACK:
                return getString(R.string.pack_super_rare_desc);
            case UNIQUE_PACK:
                return getString(R.string.pack_unique_desc);
            case FREE:
            default:
                return getString(R.string.pack_free_desc);
        }
    }

    private void onPackClicked(PackType type) {
        GameRepository repo = GameRepository.get();
        if (type == PackType.FREE) {
            long remaining = repo.freePackRemainingMs();
            if (remaining > 0) {
                Toast.makeText(requireContext(),
                        "Free pack in " + Format.timeLeft(remaining), Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(requireContext(), PackOpeningActivity.class);
            intent.putExtra(PackOpeningActivity.EXTRA_MODE, PackOpeningActivity.MODE_FREE);
            startActivity(intent);
            return;
        }
        if (repo.getPawns() < type.price) {
            Toast.makeText(requireContext(), "Not enough Pawns", Toast.LENGTH_SHORT).show();
            return;
        }
        for (Rarity r : type.contents) {
            if (r.isLimitedSupply() && !repo.canMintAny(r)) {
                Toast.makeText(requireContext(),
                        r.displayName + " cards are sold out this season", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        Intent intent = new Intent(requireContext(), PackOpeningActivity.class);
        intent.putExtra(PackOpeningActivity.EXTRA_MODE, PackOpeningActivity.MODE_BUY);
        intent.putExtra(PackOpeningActivity.EXTRA_PACK, type.name());
        startActivity(intent);
    }

    private class PackAdapter extends RecyclerView.Adapter<PackAdapter.Holder> {

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_pack, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            GameRepository repo = GameRepository.get();
            PackType type = PackType.values()[position];

            holder.root.setBackgroundResource(CardAdapter.backgroundFor(type.tier));
            int primary = CardAdapter.textColorFor(type.tier);
            int secondary = CardAdapter.secondaryTextColorFor(type.tier);

            holder.name.setText(type.displayName);
            holder.name.setTextColor(primary);
            holder.desc.setText(descriptionFor(type));
            holder.desc.setTextColor(secondary);
            holder.icon.setColorFilter(primary);

            if (type.tier.isLimitedSupply()) {
                int remaining = 0;
                for (com.chessfantasy.israel.model.Player p : repo.getPlayers()) {
                    remaining += repo.remainingSupply(p.id, type.tier);
                }
                holder.supply.setText(Format.pawns(remaining) + " " + type.tier.displayName
                        + " cards left this season");
            } else {
                holder.supply.setText("Unlimited supply");
            }
            holder.supply.setTextColor(secondary);

            if (type == PackType.FREE) {
                long remaining = repo.freePackRemainingMs();
                holder.buy.setText(remaining <= 0 ? "FREE" : Format.timeLeft(remaining));
            } else {
                holder.buy.setText(Format.pawns(type.price));
            }
            holder.buy.setOnClickListener(v -> onPackClicked(type));
            holder.root.setOnClickListener(v -> onPackClicked(type));
        }

        private String descriptionFor(PackType type) {
            switch (type) {
                case LIMITED_PACK:
                    return getString(R.string.pack_limited_desc);
                case RARE_PACK:
                    return getString(R.string.pack_rare_desc);
                case SUPER_RARE_PACK:
                    return getString(R.string.pack_super_rare_desc);
                case UNIQUE_PACK:
                    return getString(R.string.pack_unique_desc);
                case FREE:
                default:
                    return getString(R.string.pack_free_desc);
            }
        }

        @Override
        public int getItemCount() {
            return PackType.values().length;
        }

        class Holder extends RecyclerView.ViewHolder {
            final View root;
            final android.widget.ImageView icon;
            final TextView name;
            final TextView desc;
            final TextView supply;
            final Button buy;

            Holder(@NonNull View itemView) {
                super(itemView);
                root = itemView.findViewById(R.id.pack_root);
                icon = itemView.findViewById(R.id.pack_icon);
                name = itemView.findViewById(R.id.pack_name);
                desc = itemView.findViewById(R.id.pack_desc);
                supply = itemView.findViewById(R.id.pack_supply);
                buy = itemView.findViewById(R.id.pack_btn_buy);
            }
        }
    }
}
