package rulescript_test

import (
	"testing"
)

func TestSimplifyAndTrue(t *testing.T) { testAndTrue(t, "SimplifyAndTrue", false) }

func TestSQLSimplifyAndTrue(t *testing.T) {
	expectSQLFires(t, "SimplifyAndTrue", "SELECT x FROM u WHERE x > 0 AND TRUE")
}
