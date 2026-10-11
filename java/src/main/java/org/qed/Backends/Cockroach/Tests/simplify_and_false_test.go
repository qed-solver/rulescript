package rulescript_test

import (
	"testing"
)

func TestSimplifyAndFalse(t *testing.T) { testSimplifyAndFalse(t, "SimplifyAndFalse", false) }

func TestSQLSimplifyAndFalse(t *testing.T) {
	expectSQLRewrites(t, "SimplifyAndFalse", "SELECT x FROM u WHERE x > 0 AND FALSE")
}
