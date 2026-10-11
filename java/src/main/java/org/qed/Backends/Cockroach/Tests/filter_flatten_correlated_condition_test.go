package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestFilterFlattenCorrelatedCondition(t *testing.T) {
	rule := loadRule(t, "FilterFlattenCorrelatedCondition")
	e := newEnv(t)
	outer, oc := e.scan("t", 1)
	inner, ic := e.scan("u", 1)
	join := e.f.ConstructInnerJoin(outer, inner, nil, &memo.JoinPrivate{})

	fcol := e.f.Metadata().AddColumn("expr", types.Int)
	items := memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.v(ic[0]), fcol)}
	passthrough := opt.MakeColSet(oc[0], ic[0])

	before := e.f.ConstructSelect(join, e.filters(e.f.ConstructGt(e.v(oc[0]), e.v(ic[0]))))
	want := e.f.ConstructProject(
		e.f.ConstructSelect(
			e.f.ConstructProject(join, items, passthrough),
			e.filters(e.f.ConstructGt(e.v(oc[0]), e.v(fcol))),
		),
		nil, passthrough,
	)
	e.expectRewrite(rule, before, want)

	leftJoin := e.f.ConstructLeftJoin(outer, inner, nil, &memo.JoinPrivate{})
	e.expectNoMatch(rule, e.f.ConstructSelect(leftJoin, e.filters(e.f.ConstructGt(e.v(oc[0]), e.v(ic[0])))))

	e.expectNoMatch(rule, e.f.ConstructSelect(join, e.filters(e.f.ConstructGt(e.v(ic[0]), e.v(oc[0])))))

	onJoin := e.f.ConstructInnerJoin(outer, inner, e.filters(e.f.ConstructEq(e.v(oc[0]), e.v(ic[0]))), &memo.JoinPrivate{})
	e.expectNoMatch(rule, e.f.ConstructSelect(onJoin, e.filters(e.f.ConstructGt(e.v(oc[0]), e.v(ic[0])))))
}
