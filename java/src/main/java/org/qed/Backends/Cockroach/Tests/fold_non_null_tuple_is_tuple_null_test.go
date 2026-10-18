package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestFoldNonNullTupleIsTupleNull(t *testing.T) {
	rule := loadRule(t, "FoldNonNullTupleIsTupleNull")
	e := newEnv(t)
	if _, err := e.cat.ExecuteDDL("CREATE TABLE tnn (a INT, b INT)"); err != nil {
		t.Fatal(err)
	}
	isNull := func(c opt.ColumnID) opt.ScalarExpr {
		return e.f.ConstructIs(e.v(c), e.f.ConstructNull(types.Int))
	}
	isNotNull := func(c opt.ColumnID) opt.ScalarExpr {
		return e.f.ConstructIsNot(e.v(c), e.f.ConstructNull(types.Int))
	}

	src, cols := e.scan("t", 2)
	before := e.f.ConstructSelect(src, e.filters(e.f.ConstructAnd(isNull(cols[0]), isNull(cols[1]))))
	want := e.f.ConstructSelect(src, e.filters(e.f.ConstructFalse()))
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, e.f.ConstructSelect(src, e.filters(e.f.ConstructOr(isNull(cols[0]), isNull(cols[1])))))
	e.expectNoMatch(rule, e.f.ConstructSelect(src, e.filters(e.f.ConstructAnd(isNull(cols[0]), isNotNull(cols[1])))))

	nullSrc, nullCols := e.scan("tnn", 2)
	e.expectNoMatch(rule, e.f.ConstructSelect(nullSrc, e.filters(e.f.ConstructAnd(isNull(nullCols[0]), isNull(nullCols[1])))))
}
