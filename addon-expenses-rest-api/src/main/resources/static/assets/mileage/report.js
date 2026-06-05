(function () {
  var url = new URL(window.location.href);
  if (url.searchParams.has("auth_token")) {
    url.searchParams.delete("auth_token");
    history.replaceState({}, document.title, url.pathname + url.search + url.hash);
  }
  var printButton = document.getElementById("btn-print");
  if (printButton) {
    printButton.addEventListener("click", function () {
      window.print();
    });
  }
})();
