package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestFoldNullTupleIsTupleNull(t *testing.T) {
	rule := loadRule(t, "FoldNullTupleIsTupleNull")
	e := newEnv(t)
	s, _ := e.scan("u", 1)
	nullInt := e.f.ConstructNull(types.Int)
	isNull := e.f.ConstructIs(nullInt, memo.NullSingleton)
	isNotNull := e.f.ConstructIsNot(nullInt, memo.NullSingleton)
	e.expectRewrite(rule, e.f.ConstructSelect(s, e.filters(isNull, isNull)), s)
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(isNull)))
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(isNull, isNotNull)))
}

func TestSQLFoldNullTupleIsTupleNull(t *testing.T) {
	expectSQLFires(t, "FoldNullTupleIsTupleNull", "SELECT x FROM u WHERE NULL IS NULL AND NULL IS NULL")
}
