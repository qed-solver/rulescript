package rulescript_test

import (
	"strings"
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func init() {
	inlineRules["FilterProjectTranspose"] = `{
  "name": "FilterProjectTranspose", "mode": "normalize",
  "from": {"op": "filter", "id": "r2",
    "cond": {"op": "pred", "name": "F", "args": [{"op": "row", "rels": ["r1"]}]},
    "input": {"op": "project", "id": "r1",
      "exprs": [{"op": "proj", "name": "Proj", "args": [{"op": "row", "rels": ["r0"]}]}],
      "input": {"op": "scan", "name": "Source", "id": "r0"}}},
  "to": {"op": "project", "id": "r4",
    "exprs": [{"op": "proj", "name": "Proj", "args": [{"op": "row", "rels": ["r0"]}]}],
    "input": {"op": "filter", "id": "r3",
      "cond": {"op": "pred", "name": "F", "args": [
        {"op": "proj", "name": "Proj", "args": [{"op": "row", "rels": ["r0"]}]}]},
      "input": {"op": "scan", "name": "Source", "id": "r0"}}}
}`
}

func TestFilterProjectTranspose(t *testing.T) {
	rule := loadRule(t, "FilterProjectTranspose")
	e := newEnv(t)
	s, c := e.scan("t", 3)

	sum := e.f.Metadata().AddColumn("sum", types.Int)
	item := func() memo.ProjectionsExpr {
		return memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(c[0]), e.v(c[1])), sum)}
	}
	proj := e.f.ConstructProject(s, item(), opt.ColSet{})
	before := e.f.ConstructSelect(proj, e.filters(e.f.ConstructGt(e.v(sum), e.num(10))))
	want := e.f.ConstructProject(
		e.f.ConstructSelect(s, e.filters(
			e.f.ConstructGt(e.f.ConstructPlus(e.v(c[0]), e.v(c[1])), e.num(10)),
		)),
		item(), opt.ColSet{},
	)
	e.expectRewrite(rule, before, want)

	before2 := e.f.ConstructSelect(proj, e.filters(
		e.f.ConstructGt(e.v(sum), e.num(10)),
		e.f.ConstructNe(e.v(sum), e.num(12)),
	))
	want2 := e.f.ConstructProject(
		e.f.ConstructSelect(s, e.filters(
			e.f.ConstructGt(e.f.ConstructPlus(e.v(c[0]), e.v(c[1])), e.num(10)),
			e.f.ConstructNe(e.f.ConstructPlus(e.v(c[0]), e.v(c[1])), e.num(12)),
		)),
		item(), opt.ColSet{},
	)
	e.expectRewrite(rule, before2, want2)

	withPass := e.f.ConstructProject(s, item(), opt.MakeColSet(c[2]))
	e.expectNoMatch(rule, e.f.ConstructSelect(withPass, e.filters(e.f.ConstructGt(e.v(sum), e.num(10)))))
	e.expectNoMatch(rule, proj)
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructGt(e.v(c[0]), e.num(10)))))
}

func TestSQLFilterProjectTranspose(t *testing.T) {
	sql := "SELECT * FROM (SELECT a + b AS s FROM t) WHERE s > 10"
	before, _ := planFor(t, sql, nil)
	if strings.Index(before, "select") > strings.Index(before, "project") {
		t.Fatalf("expected the select above the project without the rule:\n%s", before)
	}
	got, fired := planFor(t, sql, nil, "FilterProjectTranspose")
	if strings.Join(fired, ",") != "FilterProjectTranspose" || strings.Index(got, "project") > strings.Index(got, "select") {
		t.Fatalf("expected the select below the project, got %v:\n%s", fired, got)
	}
	t.Logf("plan:\n%s", got)
}
