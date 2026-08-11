#!/data/data/com.termux/files/usr/bin/bash
cd "$(dirname "$0")" || exit 1

MSG="$1"
if [ -z "$MSG" ]; then
  MSG="update"
fi

REPO="TubeHQApp"
OWNER="Sekiguchi-Takashi"
TOKEN="$(git config --global github.token)"

if [ -z "$TOKEN" ]; then
  printf 'github.token が未設定です\n'
  exit 1
fi

curl -s -o /dev/null -X POST \
  -H "Authorization: token $TOKEN" \
  https://api.github.com/user/repos \
  -d "{\"name\":\"$REPO\",\"private\":true}"

if [ ! -d .git ]; then
  git init -q
  git branch -M main
fi

git config user.name "$OWNER"
git config user.email "$OWNER@users.noreply.github.com"
git config http.postBuffer 524288000
git config http.version HTTP/1.1

git remote remove origin 2>/dev/null
git remote add origin "https://$OWNER:$TOKEN@github.com/$OWNER/$REPO.git"

git add -A
git commit -q -m "$MSG" || printf '変更なし\n'
git push -u origin main

printf '\n--- リモート確認 ---\n'
git ls-remote origin
