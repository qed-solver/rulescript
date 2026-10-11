package rulescript_test

import (
	"testing"
)

func TestSimplifyOrTrue(t *testing.T) { testSimplifyOrLiteral(t, "SimplifyOrTrue", true, false) }

func TestSQLSimplifyOrTrue(t *testing.T) {
	expectSQLRewrites(t, "SimplifyOrTrue", "SELECT x FROM u WHERE x > 0 OR TRUE")
}
