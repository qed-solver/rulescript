package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestFoldNullTupleIsTupleNotNull(t *testing.T) {
	rule := loadRule(t, "FoldNullTupleIsTupleNotNull")
	e := newEnv(t)
	s, cols := e.scan("u", 1)
	nullInt := e.f.ConstructNull(types.Int)
	first := e.f.ConstructIsNot(nullInt, memo.NullSingleton)
	second := e.f.ConstructIsNot(e.v(cols[0]), memo.NullSingleton)
	e.expectRewrite(rule, e.f.ConstructSelect(s, e.filters(first, second)), e.f.ConstructSelect(s, e.filters(e.f.ConstructFalse())))
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(second, first)))
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(first, e.f.ConstructIs(e.v(cols[0]), memo.NullSingleton))))
}

func TestSQLFoldNullTupleIsTupleNotNull(t *testing.T) {
	expectSQLFires(t, "FoldNullTupleIsTupleNotNull", "SELECT x FROM u WHERE NULL IS NOT NULL AND x IS NOT NULL")
}
