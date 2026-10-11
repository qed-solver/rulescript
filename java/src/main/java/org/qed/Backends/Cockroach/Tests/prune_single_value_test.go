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
	s, c := e.scan("t", 3)

	vcol := e.f.Metadata().AddColumn("v", types.Bool)
	values := e.f.ConstructValues(memo.ScalarListExpr{
		e.f.ConstructTuple(
			memo.ScalarListExpr{e.f.ConstructTrue()},
			types.MakeTuple([]*types.T{types.Bool}),
		),
	}, &memo.ValuesPrivate{
		Cols: opt.ColList{vcol},
		ID:   e.f.Metadata().NextUniqueID(),
	})
	on := e.filters(e.v(vcol))
	before := e.f.ConstructInnerJoin(s, values, on, &memo.JoinPrivate{})

	trueCol := e.f.Metadata().AddColumn("true", types.Bool)
	want := e.f.ConstructProject(
		e.f.ConstructSelect(s, e.filters(e.f.ConstructTrue())),
		memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.f.ConstructTrue(), trueCol)},
		opt.MakeColSet(c[0]),
	)
	e.expectRewrite(rule, before, want)

	u, uc := e.scan("u", 2)
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(s, u, e.filters(e.f.ConstructEq(e.v(c[0]), e.v(uc[0]))), &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(s, values, nil, &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructLeftJoin(s, values, on, &memo.JoinPrivate{}))
}
