# PDF delivery report from the production nginx log (read-only). Run on the box from Windows as:
#   cmd /c "ssh exam-kvm bash -s < docs\exam-site\reports\pdf-log-probe.sh"
# (Inline ssh commands from PowerShell lose their quotes; always pipe a script like this one.)
#
# What it shows, per PDF kind: requests, cut-off transfers (fewer bytes than the largest copy of the same path),
# retry bursts (4+ loads of the same file by the same address within 2 minutes: the viewer failing and the user pressing
# Retry), bytes per request, and download time (rt=, logged since 22 Nov 2026). Added with the PDF viewer fix plan.
set -u
TMP=$(mktemp)
trap 'rm -f "$TMP"' EXIT
( for f in /var/log/nginx/access.log*; do case $f in *.gz) zcat "$f";; *) cat "$f";; esac; done ) 2>/dev/null \
  | grep -E 'GET /api/v1/(live-exams|live-exam-participants)/[^ ]*(question-pdf|answer-pdf|answer-draft|evaluated-pdf)' > "$TMP"

echo "== per kind: requests, median and max MB per request, cut-off transfers, median and max seconds (rt)"
awk '{
  split($7,u,"?"); p=u[1]; k=p; sub(/.*\//,"",k); b=$10+0;
  key[NR]=p; kind[NR]=k; bytes[NR]=b; if (b>mx[p]) mx[p]=b;
  rt[NR]=-1; for (i=11;i<=NF;i++) if ($i ~ /^rt=/) { v=$i; sub(/^rt=/,"",v); rt[NR]=v+0 }
} END {
  for (i=1;i<=NR;i++) { k=kind[i]; n[k]++; B[k,n[k]]=bytes[i]; if (bytes[i] < 0.9*mx[key[i]]) cut[k]++;
    if (rt[i]>=0) { m[k]++; R[k,m[k]]=rt[i] } }
  for (k in n) {
    # medians by simple selection (small counts)
    medb=0; for (i=1;i<=n[k];i++){c=0; for(j=1;j<=n[k];j++) if (B[k,j]<=B[k,i]) c++; if (c>=n[k]/2){medb=B[k,i]; break}}
    maxb=0; for (i=1;i<=n[k];i++) if (B[k,i]>maxb) maxb=B[k,i];
    medr="-"; maxr="-"; if (m[k]>0) { maxr=0; for (i=1;i<=m[k];i++){ if (R[k,i]>maxr) maxr=R[k,i]; c=0; for(j=1;j<=m[k];j++) if (R[k,j]<=R[k,i]) c++; if (c>=m[k]/2 && medr=="-") medr=R[k,i] } }
    printf "%-14s req=%4d medMB=%6.2f maxMB=%6.2f cut=%3d medS=%s maxS=%s\n", k, n[k], medb/1048576, maxb/1048576, cut[k]+0, medr, maxr
  }
}' "$TMP" | sort

echo "== retry bursts (>=4 loads of one file by one address within 2 min): day, kind, loads, browser"
awk -F'"' '{
  split($1,h," "); t=substr(h[4],2); split(t,d,":"); s=d[2]*3600+d[3]*60+d[4]; split($2,r," "); p=r[2]; sub(/\?.*/,"",p);
  k=h[1]"|"p"|"d[1]; n[k]++; T[k,n[k]]=s; U[k,n[k]]=$6 }
 END { for (k in n){ best=1; at=1; for(i=1;i<=n[k];i++){c=0; for(j=i;j<=n[k];j++) if(T[k,j]>=T[k,i] && T[k,j]-T[k,i]<=120)c++; if(c>best){best=c; at=i}}
   if(best>=4){ split(k,a,"|"); e=a[2]; sub(/.*\//,"",e); u=U[k,at]; match(u,/(Chrome|Version|FBAV|Firefox)\/[0-9]+/); printf "%s %-13s loads=%2d %s\n", a[3], e, best, substr(u,RSTART,RLENGTH) } } }' "$TMP" | sort

echo "== viewer failures reported by browsers (API log, last 7 days)"
journalctl -u coaching-api --since '-7 days' --no-pager 2>/dev/null | grep -o 'client_event .*' | sed -E 's/ua="([^"]{0,80}).*/ua="\1..."/' | tail -20
echo "== viewing copies made (API log, last 7 days)"
journalctl -u coaching-api --since '-7 days' --no-pager 2>/dev/null | grep -cE 'pdf view copy made' || true
