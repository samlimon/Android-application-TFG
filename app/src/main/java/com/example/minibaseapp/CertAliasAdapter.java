package com.example.minibaseapp;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.example.minibaseapp.crypto.ImportedCert;

import java.util.List;

public class CertAliasAdapter extends RecyclerView.Adapter<CertAliasAdapter.CertViewHolder> {

    public interface OnCertClickListener {
        void onCertClick(String alias);
    }

    private final List<ImportedCert> certs;
    private final OnCertClickListener listener;
    private int selectedPosition = RecyclerView.NO_POSITION;

    public CertAliasAdapter(List<ImportedCert> certs, OnCertClickListener listener) {
        this.certs = certs;
        this.listener = listener;
    }

    @NonNull
    @Override
    public CertViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_cert_alias, parent, false);
        return new CertViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CertViewHolder holder, int position) {
        ImportedCert cert = certs.get(position);
        holder.tvAlias.setText(cert.alias);

        // Sombreado simple para seleccionado / no seleccionado
        if (position == selectedPosition) {
            holder.cardView.setCardBackgroundColor(0xFFE0E0E0); // gris claro
        } else {
            holder.cardView.setCardBackgroundColor(0xFFFFFFFF); // blanco
        }

        holder.itemView.setOnClickListener(v -> {
            int oldPos = selectedPosition;
            selectedPosition = holder.getAdapterPosition();
            notifyItemChanged(oldPos);
            notifyItemChanged(selectedPosition);

            listener.onCertClick(cert.alias);
        });
    }

    @Override
    public int getItemCount() {
        return certs.size();
    }

    static class CertViewHolder extends RecyclerView.ViewHolder {
        TextView tvAlias;
        CardView cardView;

        public CertViewHolder(@NonNull View itemView) {
            super(itemView);
            tvAlias = itemView.findViewById(R.id.tvAlias);
            cardView = itemView.findViewById(R.id.cardCert);
        }
    }
}
