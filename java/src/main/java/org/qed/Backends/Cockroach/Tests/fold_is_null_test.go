package rulescript_test

import (
	"testing"
)

func TestFoldIsNull(t *testing.T) { testFoldIsNullLiteral(t, "FoldIsNull", false) }

func TestSQLFoldIsNull(t *testing.T) {
	expectSQLFires(t, "FoldIsNull", "SELECT x FROM u WHERE NULL IS NULL")
}
