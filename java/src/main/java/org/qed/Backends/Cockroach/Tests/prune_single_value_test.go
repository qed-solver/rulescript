package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestPruneSingleValue(t *testing.T) {
	rule := loadRule(t, "PruneSingleValue")
	e := newEnv(t)
	s, c := e.scan("t", 1)

	boolValues := func(lit opt.ScalarExpr) (memo.RelExpr, opt.ColumnID) {
		col := e.f.Metadata().AddColumn("v", types.Bool)
		rows := memo.ScalarListExpr{
			e.f.ConstructTuple(memo.ScalarListExpr{lit}, types.MakeTuple([]*types.T{types.Bool})),
		}
		return e.f.ConstructValues(rows, &memo.ValuesPrivate{
			Cols: opt.ColList{col},
			ID:   e.f.Metadata().NextUniqueID(),
		}), col
	}

	values, vcol := boolValues(e.f.ConstructTrue())
	on := e.filters(e.v(vcol))
	before := e.f.ConstructInnerJoin(s, values, on, &memo.JoinPrivate{})
	want := e.f.ConstructProject(
		e.f.ConstructSelect(s, e.filters(e.f.ConstructTrue())),
		memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.f.ConstructTrue(), vcol)},
		opt.MakeColSet(c[0]),
	)
	e.expectRewrite(rule, before, want)

	u, uc := e.scan("u", 2)
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(s, u, e.filters(e.f.ConstructEq(e.v(c[0]), e.v(uc[0]))), &memo.JoinPrivate{}))

	falseValues, fcol := boolValues(e.f.ConstructFalse())
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(s, falseValues, e.filters(e.v(fcol)), &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructLeftJoin(s, values, on, &memo.JoinPrivate{}))
}
